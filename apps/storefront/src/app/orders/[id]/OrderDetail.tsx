'use client';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { formatDate, formatPaise, ORDER_STATUS_LABEL } from '@/lib/format';
import { loadRazorpay, type RazorpayResponse } from '@/lib/razorpay';
import type { OrderView } from '@/lib/types';

interface CheckoutOptions {
  keyId: string; razorpayOrderId: string; amountPaise: number; currency: string; orderNumber: string;
  merchantName: string; description: string; prefill: { name: string; email: string; contact: string }; testMode: boolean;
}

export function OrderDetail({ id }: { id: string }) {
  const params = useSearchParams();
  const [order, setOrder] = useState<OrderView | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [payState, setPayState] = useState<'idle' | 'opening' | 'verifying' | 'pending' | 'failed' | 'dismissed'>('idle');
  const [payMessage, setPayMessage] = useState<string | null>(null);
  const autoLaunched = useRef(false);

  const load = useCallback(async () => {
    try {
      const o = await api<OrderView>(`/api/v1/orders/${encodeURIComponent(id)}`);
      setOrder(o);
      return o;
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'ERROR', 'Could not load this order.'));
      return null;
    }
  }, [id]);
  useEffect(() => { load(); }, [load]);

  const poll = useCallback(async () => {
    for (let i = 0; i < 20; i++) {
      await new Promise((r) => setTimeout(r, 3000));
      const o = await load();
      if (o && o.status !== 'PENDING_PAYMENT') { setPayState('idle'); return; }
    }
    setPayMessage('We are still waiting for confirmation from the payment provider. This page will update when it arrives — you will not be charged twice.');
  }, [load]);

  const pay = useCallback(async () => {
    if (!order) return;
    setPayState('opening');
    setPayMessage(null);
    try {
      const [opts, Razorpay] = await Promise.all([
        api<CheckoutOptions>('/api/v1/payments/razorpay/orders', { method: 'POST', json: { orderId: order.id } }),
        loadRazorpay(),
      ]);
      const rzp = new Razorpay({
        key: opts.keyId,
        order_id: opts.razorpayOrderId,
        amount: opts.amountPaise,
        currency: opts.currency,
        name: opts.merchantName,
        description: opts.description,
        prefill: opts.prefill,
        notes: { order_number: opts.orderNumber },
        theme: { color: '#352433' },
        modal: { ondismiss: () => setPayState((s) => (s === 'opening' ? 'dismissed' : s)) },
        handler: async (r: RazorpayResponse) => {
          setPayState('verifying');
          try {
            const res = await api<{ paymentStatus: string; orderStatus: string }>('/api/v1/payments/razorpay/verify', {
              method: 'POST',
              json: { orderId: order.id, razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature },
            });
            if (res.paymentStatus === 'CAPTURED') { await load(); setPayState('idle'); }
            else if (res.paymentStatus === 'FAILED') { setPayState('failed'); setPayMessage('The test payment failed. You can try again.'); }
            else { setPayState('pending'); poll(); }
          } catch (e) {
            setPayState('failed');
            setPayMessage(e instanceof ApiError ? e.message : 'We could not verify the payment.');
          }
        },
      });
      rzp.on('payment.failed', (f) => {
        setPayState('failed');
        setPayMessage(f.error?.description ?? 'The test payment failed. You can try again.');
        api('/api/v1/payments/razorpay/failure', { method: 'POST', json: { orderId: order.id, razorpayOrderId: opts.razorpayOrderId, code: f.error?.code?.slice(0, 60), description: f.error?.description?.slice(0, 300) } }).catch(() => {});
      });
      rzp.open();
    } catch (e) {
      setPayState('failed');
      setPayMessage(e instanceof ApiError ? e.message : 'Could not start the payment. Please try again.');
    }
  }, [order, load, poll]);

  useEffect(() => {
    if (order?.status === 'PENDING_PAYMENT' && params.get('pay') === '1' && !autoLaunched.current) {
      autoLaunched.current = true;
      pay();
    }
  }, [order, params, pay]);

  if (error) {
    return (
      <div className="card mx-auto max-w-lg p-10 text-center">
        <h1 className="font-display text-3xl text-plum">{error.status === 404 ? 'Order not found' : 'Could not load this order'}</h1>
        <p className="mt-3 text-muted">{error.status === 404 ? 'Orders can be viewed from the device they were placed on, or looked up with your order number and email.' : error.message}</p>
        <Link href="/track" className="btn-primary mt-6">Track an order</Link>
      </div>
    );
  }
  if (!order) return <div className="mx-auto max-w-3xl space-y-4" aria-busy="true"><div className="skeleton h-12 w-2/3" /><div className="skeleton h-64" /></div>;

  const placed = params.get('placed') === '1' || (params.get('pay') === '1' && order.status === 'CONFIRMED');
  const awaiting = order.status === 'PENDING_PAYMENT' && order.paymentMethod === 'ONLINE';

  return (
    <div className="mx-auto max-w-4xl">
      {placed && order.status === 'CONFIRMED' && (
        <div className="mb-10 rounded-[24px] bg-plum p-8 text-ivory sm:p-10" role="status">
          <p className="eyebrow text-gold">Thank you</p>
          <h1 className="mt-2 font-display text-4xl sm:text-5xl">Your order is confirmed</h1>
          <p className="mt-3 text-ivory/85">
            {order.paymentMethod === 'COD' ? `Please keep ${formatPaise(order.totals.totalPaise)} ready in cash at delivery.` : 'Your test payment was verified. No real money was charged.'} A confirmation has been queued for your email.
          </p>
        </div>
      )}

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">Order</p>
          <h2 className="mt-1 font-display text-4xl text-plum">{order.orderNumber}</h2>
          <p className="mt-1 text-sm text-muted">Placed {formatDate(order.createdAt)} · {order.cartType === 'BULK' ? 'Bulk order' : 'Retail order'}</p>
        </div>
        <span className={`rounded-full px-4 py-2 text-sm font-semibold ${order.status === 'CONFIRMED' ? 'bg-success/10 text-success' : ['CANCELLED', 'EXPIRED'].includes(order.status) ? 'bg-danger/10 text-danger' : 'bg-gold/20 text-gold-deep'}`}>
          {ORDER_STATUS_LABEL[order.status] ?? order.status}
        </span>
      </div>

      {awaiting && (
        <section className="mt-8 rounded-[20px] border-2 border-dashed border-gold bg-white p-6 sm:p-8" aria-labelledby="pay-h">
          <p className="inline-block rounded-full bg-charcoal px-3 py-1 text-xs font-bold uppercase tracking-[0.18em] text-gold">Test payment — no real money</p>
          <h3 id="pay-h" className="mt-4 font-display text-3xl text-plum">Complete your payment</h3>
          <p className="mt-2 text-sm text-muted">
            This store uses Razorpay <strong>Test Mode</strong>. Use Razorpay’s test UPI ID or test cards; nothing is charged.
            {order.paymentDueBy ? ` Items are held for you until ${formatDate(order.paymentDueBy)}.` : ''}
          </p>
          {payMessage && <p className="mt-4 rounded-lg bg-ivory-deep px-4 py-3 text-sm" role="alert">{payMessage}</p>}
          {payState === 'dismissed' && <p className="mt-4 text-sm text-muted" role="status">Payment window closed without paying. You can try again.</p>}
          <button type="button" className="btn-primary mt-6" onClick={pay} disabled={payState === 'opening' || payState === 'verifying' || payState === 'pending'}>
            {payState === 'verifying' ? 'Verifying payment…' : payState === 'pending' ? 'Waiting for confirmation…' : `Pay ${formatPaise(order.totals.totalPaise)} (test)`}
          </button>
        </section>
      )}

      <div className="mt-10 grid gap-8 md:grid-cols-[1.4fr_1fr]">
        <section className="card p-6" aria-labelledby="items-h">
          <h3 id="items-h" className="font-display text-2xl text-plum">Items</h3>
          <ul className="mt-4 divide-y divide-line text-sm">
            {order.items.map((i) => (
              <li key={i.sku} className="flex justify-between gap-4 py-3">
                <span>{i.productName} <span className="text-muted">· {i.colorName} × {i.quantity}</span></span>
                <span>{formatPaise(i.lineTotalPaise)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-4 space-y-1.5 border-t border-line pt-4 text-sm">
            <div className="flex justify-between"><dt>Subtotal</dt><dd>{formatPaise(order.totals.subtotalPaise)}</dd></div>
            {order.totals.discountPaise > 0 && <div className="flex justify-between text-success"><dt>Discount {order.couponCode ? `(${order.couponCode})` : ''}</dt><dd>−{formatPaise(order.totals.discountPaise)}</dd></div>}
            <div className="flex justify-between"><dt>Shipping</dt><dd>{order.totals.shippingPaise ? formatPaise(order.totals.shippingPaise) : 'Free'}</dd></div>
            {order.totals.codFeePaise > 0 && <div className="flex justify-between"><dt>COD fee</dt><dd>{formatPaise(order.totals.codFeePaise)}</dd></div>}
            <div className="flex justify-between pt-2 text-base font-semibold"><dt>Total</dt><dd>{formatPaise(order.totals.totalPaise)}</dd></div>
            <div className="flex justify-between text-xs text-muted"><dt>Includes GST</dt><dd>{formatPaise(order.totals.taxIncludedPaise, true)}</dd></div>
          </dl>
        </section>
        <div className="space-y-8">
          <section className="card p-6" aria-labelledby="ship-h">
            <h3 id="ship-h" className="font-display text-2xl text-plum">Delivering to</h3>
            <address className="mt-3 text-sm not-italic leading-relaxed">
              {order.contactName}<br />{order.shippingAddress.line1}{order.shippingAddress.line2 ? <>, {order.shippingAddress.line2}</> : null}<br />
              {order.shippingAddress.city}, {order.shippingAddress.state} {order.shippingAddress.pincode}
            </address>
            <p className="mt-3 text-sm text-muted">Payment: {order.paymentMethod === 'COD' ? 'Cash on delivery' : 'Online (Razorpay test mode)'}</p>
          </section>
          <section className="card p-6" aria-labelledby="time-h">
            <h3 id="time-h" className="font-display text-2xl text-plum">Timeline</h3>
            <ol className="mt-4 space-y-3 border-l border-gold/60 pl-5 text-sm">
              {order.timeline.map((t, i) => (
                <li key={i} className="relative">
                  <span aria-hidden className="absolute -left-[1.6rem] top-1.5 h-2.5 w-2.5 rounded-full bg-gold" />
                  <span className="font-medium">{t.note}</span><br /><span className="text-muted">{formatDate(t.at)}</span>
                </li>
              ))}
            </ol>
          </section>
        </div>
      </div>
      <div className="mt-10 flex gap-3">
        <Link href="/collections/all" className="btn-outline">Continue shopping</Link>
        <Link href="/orders" className="btn-ghost">My orders</Link>
      </div>
    </div>
  );
}
