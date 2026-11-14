'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api, ApiError } from '@/lib/api';
import { formatDate, formatPaise, ORDER_STATUS_LABEL } from '@/lib/format';
import type { OrderSummary } from '@/lib/types';

export default function MyOrdersPage() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    document.title = 'My orders · Era Wovens';
    api<OrderSummary[]>('/api/v1/me/orders').then(setOrders).catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load orders.'));
  }, []);
  return (
    <div className="container-x max-w-4xl py-12">
      <h1 className="font-display text-5xl text-plum">My orders</h1>
      <p className="mt-3 text-muted">Orders placed from this browser. Placed an order elsewhere? <Link href="/track" className="font-semibold text-gold-deep underline">Track it with your order number</Link>.</p>
      {error && <p role="alert" className="mt-8 text-danger">{error}</p>}
      {!orders && !error && <div className="mt-8 space-y-3" aria-busy="true">{[0, 1].map((i) => <div key={i} className="skeleton h-20" />)}</div>}
      {orders && orders.length === 0 && (
        <div className="card mt-8 p-10 text-center"><p className="text-muted">No orders yet.</p><Link href="/collections/all" className="btn-primary mt-5">Start shopping</Link></div>
      )}
      {orders && orders.length > 0 && (
        <ul className="mt-8 space-y-3">
          {orders.map((o) => (
            <li key={o.id}>
              <Link href={`/orders/${o.id}`} className="card flex flex-wrap items-center justify-between gap-4 p-5 transition hover:border-plum">
                <span><span className="font-display text-xl text-plum">{o.orderNumber}</span><span className="block text-sm text-muted">{formatDate(o.createdAt)} · {o.units} {o.units === 1 ? 'piece' : 'pieces'} · {o.paymentMethod === 'COD' ? 'COD' : 'Online (test)'}</span></span>
                <span className="text-right"><span className="block font-semibold">{formatPaise(o.totalPaise)}</span><span className="text-sm text-muted">{ORDER_STATUS_LABEL[o.status] ?? o.status}</span></span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
