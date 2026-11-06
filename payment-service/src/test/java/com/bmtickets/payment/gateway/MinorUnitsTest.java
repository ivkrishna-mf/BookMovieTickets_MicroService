package com.bmtickets.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MinorUnitsTest {

    @Test
    void convertsTwoDecimalCurrencies() {
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("499.50"), "INR")).isEqualTo(49950L);
    }

    @Test
    void convertsZeroDecimalCurrencies() {
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("1500"), "JPY")).isEqualTo(1500L);
    }
}
