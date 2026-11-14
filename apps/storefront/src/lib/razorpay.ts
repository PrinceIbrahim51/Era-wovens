'use client';
// Loads the official Razorpay Checkout script on demand. Only the public TEST key id reaches the browser.

export interface RazorpayResponse { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string }
interface RazorpayFailure { error?: { code?: string; description?: string; reason?: string } }
interface RazorpayInstance { open(): void; on(event: 'payment.failed', cb: (r: RazorpayFailure) => void): void }
type RazorpayCtor = new (options: Record<string, unknown>) => RazorpayInstance;

declare global { interface Window { Razorpay?: RazorpayCtor } }

const SRC = 'https://checkout.razorpay.com/v1/checkout.js';
let loading: Promise<RazorpayCtor> | null = null;

export function loadRazorpay(): Promise<RazorpayCtor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  loading ??= new Promise<RazorpayCtor>((resolve, reject) => {
    const s = document.createElement('script');
    s.src = SRC;
    s.async = true;
    s.onload = () => (window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay unavailable')));
    s.onerror = () => { loading = null; reject(new Error('Could not load Razorpay checkout')); };
    document.head.appendChild(s);
  });
  return loading;
}

export type { RazorpayFailure };
