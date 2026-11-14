'use client';
import { useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { formatDate, formatPaise, ORDER_STATUS_LABEL } from '@/lib/format';
import type { OrderView } from '@/lib/types';

export default function TrackPage() {
  const [order, setOrder] = useState<OrderView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const orderNumber = String(fd.get('orderNumber') ?? '').trim().toUpperCase();
    const email = String(fd.get('email') ?? '').trim();
    setError(null); setOrder(null);
    if (!/^ERA-\d{6}-[A-Z0-9]{6}$/.test(orderNumber)) { setError('Order numbers look like ERA-261114-ABC123.'); return; }
    setBusy(true);
    try {
      setOrder(await api<OrderView>('/api/v1/orders/lookup', { method: 'POST', json: { orderNumber, email } }));
    } catch (err) {
      setError(err instanceof ApiError && err.status === 404 ? 'We couldn’t find an order with those details.' : err instanceof ApiError ? err.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container-x max-w-2xl py-12">
      <h1 className="font-display text-5xl text-plum">Track your order</h1>
      <p className="mt-3 text-muted">Enter the order number from your confirmation and the email you used at checkout.</p>
      <form onSubmit={submit} className="card mt-8 grid gap-5 p-6 sm:grid-cols-2" noValidate>
        <div><label htmlFor="on" className="label">Order number</label><input id="on" name="orderNumber" required placeholder="ERA-261114-ABC123" className="field uppercase" autoComplete="off" /></div>
        <div><label htmlFor="em" className="label">Email</label><input id="em" name="email" type="email" required className="field" autoComplete="email" /></div>
        <button className="btn-primary sm:col-span-2" disabled={busy}>{busy ? 'Looking up…' : 'Track order'}</button>
        {error && <p role="alert" className="text-sm text-danger sm:col-span-2">{error}</p>}
      </form>
      {order && (
        <section className="card mt-8 p-6" aria-live="polite">
          <div className="flex items-center justify-between"><h2 className="font-display text-3xl text-plum">{order.orderNumber}</h2><span className="rounded-full bg-gold/20 px-3 py-1 text-sm font-semibold text-gold-deep">{ORDER_STATUS_LABEL[order.status] ?? order.status}</span></div>
          <p className="mt-2 text-sm text-muted">Hi {order.contactName} — {order.items.reduce((n, i) => n + i.quantity, 0)} pieces, {formatPaise(order.totals.totalPaise)}, delivering to {order.shippingAddress.city} {order.shippingAddress.pincode}.</p>
          <ol className="mt-5 space-y-2 border-l border-gold/60 pl-5 text-sm">
            {order.timeline.map((t, i) => <li key={i}><span className="font-medium">{t.note}</span> <span className="text-muted">· {formatDate(t.at)}</span></li>)}
          </ol>
        </section>
      )}
    </div>
  );
}
