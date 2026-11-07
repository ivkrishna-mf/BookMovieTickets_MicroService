import type { Stripe, StripeElements } from '@stripe/stripe-js';
import { CreditCard } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { config } from '../config';

interface StripePaymentProps {
  clientSecret: string;
  onSubmitted: () => void;
  onError: (message: string) => void;
  disabled?: boolean;
}

/**
 * Stripe Payment Element (PAYMENT_PROVIDER=stripe). The card data never touches our servers;
 * confirmation triggers Stripe's webhook -> payment-service -> booking saga -> CONFIRMED.
 */
export function StripePayment({ clientSecret, onSubmitted, onError, disabled }: StripePaymentProps) {
  const mountRef = useRef<HTMLDivElement>(null);
  const [stripe, setStripe] = useState<Stripe | null>(null);
  const [elements, setElements] = useState<StripeElements | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const { loadStripe } = await import('@stripe/stripe-js');
      const instance = await loadStripe(config.stripePublishableKey);
      if (!instance || cancelled || !mountRef.current) return;
      const els = instance.elements({ clientSecret, appearance: { theme: 'night', variables: { colorPrimary: '#ff3d81' } } });
      els.create('payment').mount(mountRef.current);
      setStripe(instance);
      setElements(els);
    })().catch(() => onError('Could not load the payment form.'));
    return () => {
      cancelled = true;
    };
  }, [clientSecret, onError]);

  const pay = async () => {
    if (!stripe || !elements) return;
    setSubmitting(true);
    const { error } = await stripe.confirmPayment({ elements, redirect: 'if_required' });
    setSubmitting(false);
    if (error) onError(error.message ?? 'Payment failed');
    else onSubmitted();
  };

  return (
    <div className="stripe-box">
      <div ref={mountRef} />
      <button className="btn btn--primary btn--lg btn--block" onClick={() => void pay()} disabled={!stripe || submitting || disabled}>
        <CreditCard size={18} aria-hidden /> {submitting ? 'Processing…' : 'Pay securely'}
      </button>
    </div>
  );
}
