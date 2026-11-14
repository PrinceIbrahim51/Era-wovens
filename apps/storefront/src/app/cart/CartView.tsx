'use client';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { ProductArt } from '@/components/ProductArt';
import { useCartSummary, useToast } from '@/components/Providers';
import { api, ApiError } from '@/lib/api';
import { formatPaise } from '@/lib/format';
import { LINE_ISSUES } from '@/lib/messages';
import type { Cart, CartLine, CartType } from '@/lib/types';

export function CartView() {
  const params = useSearchParams();
  const router = useRouter();
  const type: CartType = params.get('type') === 'bulk' ? 'BULK' : 'REGULAR';
  const [cart, setCart] = useState<Cart | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const toast = useToast();
  const { summary, refresh } = useCartSummary();

  const load = useCallback(() => {
    setError(null);
    api<Cart>(`/api/v1/cart?type=${type}`).then(setCart).catch((e) => setError(e instanceof ApiError ? e.message : 'Could not load your bag.'));
  }, [type]);
  useEffect(() => { setCart(null); load(); }, [load]);

  async function mutate(line: CartLine, quantity: number | null) {
    setBusy(line.itemId);
    try {
      const next = quantity === null
        ? await api<Cart>(`/api/v1/cart/items/${line.itemId}`, { method: 'DELETE' })
        : await api<Cart>(`/api/v1/cart/items/${line.itemId}`, { method: 'PATCH', json: { quantity } });
      setCart(next);
      refresh();
    } catch (e) {
      toast({ tone: 'error', message: e instanceof ApiError ? e.message : 'Could not update your bag.' });
      load();
    } finally {
      setBusy(null);
    }
  }

  const tabs: [CartType, string, number][] = [['REGULAR', 'Bag', summary.regularUnits], ['BULK', 'Bulk order', summary.bulkUnits]];

  return (
    <>
      <h1 className="font-display text-5xl text-plum">Your bag</h1>
      <div role="tablist" aria-label="Cart type" className="mt-8 inline-flex rounded-full border border-line bg-white p-1">
        {tabs.map(([t, label, n]) => (
          <button key={t} role="tab" aria-selected={type === t} type="button"
            onClick={() => router.replace(t === 'BULK' ? '/cart?type=bulk' : '/cart')}
            className={`rounded-full px-5 py-2 text-sm font-semibold transition ${type === t ? 'bg-plum text-ivory' : 'text-plum hover:bg-ivory-deep'}`}>
            {label} {n > 0 ? <span className="ml-1 opacity-75">({n})</span> : null}
          </button>
        ))}
      </div>
      {type === 'BULK' && <p className="mt-3 text-sm text-muted">Bulk orders are checked out separately from your regular bag.</p>}

      {error ? (
        <div className="card mt-10 p-10 text-center" role="alert">
          <p className="text-danger">{error}</p>
          <button className="btn-outline mt-4" onClick={load}>Retry</button>
        </div>
      ) : !cart ? (
        <div className="mt-10 grid gap-10 lg:grid-cols-[1fr_22rem]" aria-busy="true">
          <div className="space-y-4">{[0, 1].map((i) => <div key={i} className="skeleton h-36" />)}</div>
          <div className="skeleton h-64" />
        </div>
      ) : cart.lines.length === 0 ? (
        <div className="card mt-10 px-8 py-20 text-center">
          <h2 className="font-display text-3xl text-plum">{type === 'BULK' ? 'Your bulk order is empty' : 'Your bag is empty'}</h2>
          <p className="mt-3 text-muted">{type === 'BULK' ? 'Add 10 or more pieces from the bulk collection.' : 'Discover handwoven sarees for every occasion.'}</p>
          <Link href={type === 'BULK' ? '/bulk-orders' : '/collections/all'} className="btn-primary mt-8">{type === 'BULK' ? 'Browse bulk styles' : 'Start shopping'}</Link>
        </div>
      ) : (
        <div className="mt-10 grid gap-10 lg:grid-cols-[1fr_22rem]">
          <ul className="divide-y divide-line border-y border-line" aria-label="Items">
            {cart.lines.map((l) => (
              <li key={l.itemId} className={`flex gap-5 py-6 transition ${busy === l.itemId ? 'opacity-50' : ''}`}>
                <Link href={l.productSlug ? `/products/${l.productSlug}` : '#'} className="w-24 shrink-0 overflow-hidden rounded-xl bg-ivory-deep sm:w-28">
                  {l.motif && l.colorHex && l.accentHex
                    ? <ProductArt uid={`cart-${l.sku}`} motif={l.motif} base={l.colorHex} accent={l.accentHex} className="aspect-[4/5] w-full" />
                    : <div className="aspect-[4/5]" />}
                </Link>
                <div className="flex min-w-0 flex-1 flex-col">
                  <div className="flex justify-between gap-4">
                    <div>
                      <h2 className="font-display text-xl text-charcoal">{l.productName}</h2>
                      <p className="text-sm text-muted">{[l.colorName, l.fabric].filter(Boolean).join(' · ')}</p>
                      {l.unitPricePaise ? <p className="mt-1 text-sm">{formatPaise(l.unitPricePaise)} {type === 'BULK' ? 'per piece (bulk)' : 'each'}</p> : null}
                    </div>
                    <p className="font-semibold">{l.lineTotalPaise !== undefined ? formatPaise(l.lineTotalPaise) : '—'}</p>
                  </div>
                  {l.issue && <p className="mt-2 text-sm font-medium text-danger" role="alert">{LINE_ISSUES[l.issue] ?? 'Needs attention.'}</p>}
                  <div className="mt-auto flex items-center gap-4 pt-3">
                    <label className="flex items-center gap-2 text-sm">
                      <span className="text-muted">Qty</span>
                      <select value={l.quantity} disabled={busy === l.itemId} onChange={(e) => mutate(l, Number(e.target.value))}
                        className="rounded-full border border-line bg-white px-3 py-1.5" aria-label={`Quantity for ${l.productName}`}>
                        {qtyOptions(l, type).map((n) => <option key={n} value={n}>{n}</option>)}
                      </select>
                    </label>
                    <button type="button" className="text-sm text-muted underline underline-offset-4 hover:text-danger" disabled={busy === l.itemId}
                      onClick={() => mutate(l, null)}>Remove</button>
                  </div>
                </div>
              </li>
            ))}
          </ul>

          <aside className="card h-fit p-6 lg:sticky lg:top-28" aria-label="Summary">
            <h2 className="font-display text-2xl text-plum">Summary</h2>
            {cart.bulk && (
              <div className="mt-5">
                <div className="flex justify-between text-sm"><span>Bulk minimum</span><span className="font-semibold">{cart.bulk.units} / {cart.bulk.minimumUnits} pieces</span></div>
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-ivory-deep" role="progressbar" aria-valuemin={0} aria-valuemax={cart.bulk.minimumUnits}
                  aria-valuenow={Math.min(cart.bulk.units, cart.bulk.minimumUnits)} aria-label="Progress to bulk minimum">
                  <div className="h-full rounded-full bg-gold transition-all" style={{ width: `${Math.min(100, (cart.bulk.units / cart.bulk.minimumUnits) * 100)}%` }} />
                </div>
                {!cart.bulk.met && <p className="mt-2 text-sm text-gold-deep">Add {Math.max(0, cart.bulk.minimumUnits - cart.bulk.units)} more {cart.bulk.allowMixedSkus ? 'pieces (any bulk styles)' : 'of each style'} to check out.</p>}
              </div>
            )}
            <dl className="mt-5 space-y-2 text-sm">
              <div className="flex justify-between"><dt>Subtotal ({cart.units} {cart.units === 1 ? 'piece' : 'pieces'})</dt><dd className="font-semibold">{formatPaise(cart.subtotalPaise)}</dd></div>
              <div className="flex justify-between text-muted"><dt>Shipping & offers</dt><dd>At checkout</dd></div>
            </dl>
            <p className="mt-3 text-xs text-muted">Prices include GST. Your final total is confirmed securely at checkout.</p>
            {cart.readyForCheckout ? (
              <Link href={type === 'BULK' ? '/checkout?type=bulk' : '/checkout'} className="btn-primary mt-6 w-full">Checkout</Link>
            ) : (
              <button className="btn-primary mt-6 w-full" disabled>Checkout</button>
            )}
            {!cart.readyForCheckout && cart.lines.some((l) => l.issue) && <p className="mt-2 text-xs text-danger">Resolve the highlighted items to continue.</p>}
          </aside>
        </div>
      )}
    </>
  );
}

function qtyOptions(l: CartLine, type: CartType): number[] {
  const max = Math.max(l.quantity, Math.min(l.maxQuantity || l.quantity, type === 'BULK' ? 500 : 10));
  if (max <= 30) return Array.from({ length: max }, (_, i) => i + 1);
  const steps = new Set<number>([...Array.from({ length: 30 }, (_, i) => i + 1), 40, 50, 75, 100, 150, 200, 300, 400, 500, l.quantity]);
  return [...steps].filter((n) => n <= max).sort((a, b) => a - b);
}
