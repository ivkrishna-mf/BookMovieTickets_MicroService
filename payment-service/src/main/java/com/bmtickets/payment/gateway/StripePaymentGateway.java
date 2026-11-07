package com.bmtickets.payment.gateway;

import com.bmtickets.common.ratelimit.BucketSpec;
import com.bmtickets.common.ratelimit.DistributedRateLimiter;
import com.bmtickets.payment.config.StripeProperties;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stripe adapter.
 * <ul>
 *   <li>Every mutating call carries a Stripe Idempotency-Key derived from the booking => retries never double-charge.</li>
 *   <li>Outbound <b>throttling</b>: a cluster-wide token bucket keeps all pods together under Stripe's rate limit;
 *       callers wait up to 2s for a token instead of being rejected by Stripe with 429.</li>
 *   <li>Circuit breaker stops hammering Stripe during an incident; the SDK's own network retries handle blips.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "stripe", matchIfMissing = true)
public class StripePaymentGateway implements PaymentGateway {

    private static final Set<String> ZERO_DECIMAL = Set.of("JPY", "KRW", "VND", "CLP", "ISK", "UGX", "XAF", "XOF");

    private final StripeProperties props;
    private final DistributedRateLimiter limiter;
    private final BucketSpec bucket;

    public StripePaymentGateway(StripeProperties props, DistributedRateLimiter limiter) {
        if (props.apiKey().isBlank()) {
            throw new IllegalStateException("app.stripe.api-key must be configured (or set app.payment.provider=fake)");
        }
        this.props = props;
        this.limiter = limiter;
        this.bucket = new BucketSpec(props.burst(), props.ratePerSecond());
    }

    @Override
    @CircuitBreaker(name = "stripe")
    public GatewayIntent createIntent(UUID bookingId, BigDecimal amount, String currency, String customerEmail) {
        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(amount, currency))
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .putMetadata("bookingId", bookingId.toString())
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build());
        if (customerEmail != null && !customerEmail.isBlank()) {
            params.setReceiptEmail(customerEmail);
        }
        PaymentIntent pi = call(() -> PaymentIntent.create(params.build(), options("pi-create-" + bookingId)));
        return new GatewayIntent(pi.getId(), pi.getClientSecret());
    }

    @Override
    @CircuitBreaker(name = "stripe")
    public CancelOutcome cancel(String providerPaymentId) {
        PaymentIntent pi = call(() -> PaymentIntent.retrieve(providerPaymentId, options(null)));
        if ("succeeded".equals(pi.getStatus())) {
            return CancelOutcome.ALREADY_SUCCEEDED;
        }
        if ("canceled".equals(pi.getStatus())) {
            return CancelOutcome.CANCELED;
        }
        try {
            call(() -> pi.cancel(PaymentIntentCancelParams.builder().build(), options("pi-cancel-" + providerPaymentId)));
            return CancelOutcome.CANCELED;
        } catch (ProviderRejectedException e) {
            // raced with a successful confirmation: re-read the authoritative state
            PaymentIntent latest = call(() -> PaymentIntent.retrieve(providerPaymentId, options(null)));
            if ("succeeded".equals(latest.getStatus())) {
                return CancelOutcome.ALREADY_SUCCEEDED;
            }
            if ("canceled".equals(latest.getStatus())) {
                return CancelOutcome.CANCELED;
            }
            throw e;
        }
    }

    @Override
    @CircuitBreaker(name = "stripe")
    public String refund(String providerPaymentId, UUID bookingId) {
        Refund r = call(() -> Refund.create(RefundCreateParams.builder().setPaymentIntent(providerPaymentId).build(),
                options("refund-" + bookingId)));
        return r.getId();
    }

    @Override
    public ProviderEvent parseWebhook(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, props.webhookSecret());
        } catch (SignatureVerificationException e) {
            throw new com.bmtickets.common.error.BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE",
                    "Webhook signature verification failed");
        }
        StripeObject object = dataObject(event);
        return switch (event.getType()) {
            case "payment_intent.succeeded" -> intentEvent(event, object, ProviderEvent.Type.SUCCEEDED);
            case "payment_intent.payment_failed" -> intentEvent(event, object, ProviderEvent.Type.FAILED_ATTEMPT);
            case "payment_intent.canceled" -> intentEvent(event, object, ProviderEvent.Type.CANCELED);
            case "charge.refunded" -> object instanceof Charge c && c.getPaymentIntent() != null
                    ? new ProviderEvent(event.getId(), ProviderEvent.Type.REFUNDED, c.getPaymentIntent(), null) : null;
            default -> null;
        };
    }

    private static ProviderEvent intentEvent(Event event, StripeObject object, ProviderEvent.Type type) {
        if (!(object instanceof PaymentIntent pi)) {
            return null;
        }
        String message = pi.getLastPaymentError() != null ? pi.getLastPaymentError().getMessage() : null;
        return new ProviderEvent(event.getId(), type, pi.getId(), message);
    }

    private static StripeObject dataObject(Event event) {
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        if (d.getObject().isPresent()) {
            return d.getObject().get();
        }
        try {
            return d.deserializeUnsafe();   // API version of the event differs from the SDK's pinned version
        } catch (EventDataObjectDeserializationException e) {
            return null;
        }
    }

    private RequestOptions options(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder b = RequestOptions.builder()
                .setApiKey(props.apiKey())
                .setMaxNetworkRetries(2)
                .setConnectTimeout(3000)
                .setReadTimeout(10000);
        if (idempotencyKey != null) {
            b.setIdempotencyKey(idempotencyKey);
        }
        return b.build();
    }

    private <T> T call(StripeCall<T> fn) {
        if (!limiter.acquire("stripe-api", bucket, Duration.ofSeconds(2))) {
            throw new ProviderUnavailableException("Local Stripe API budget exhausted", null);
        }
        try {
            return fn.execute();
        } catch (RateLimitException | ApiConnectionException | ApiException e) {   // RateLimitException first: it extends InvalidRequestException
            throw new ProviderUnavailableException("Stripe temporarily unavailable: " + e.getMessage(), e);
        } catch (InvalidRequestException e) {
            throw new ProviderRejectedException(e.getMessage());
        } catch (StripeException e) {
            throw new ProviderRejectedException(e.getMessage());
        }
    }

    static long toMinorUnits(BigDecimal amount, String currency) {
        int scale = ZERO_DECIMAL.contains(currency.toUpperCase(Locale.ROOT)) ? 0 : 2;
        return amount.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact();
    }

    @FunctionalInterface
    private interface StripeCall<T> {
        T execute() throws StripeException;
    }
}
