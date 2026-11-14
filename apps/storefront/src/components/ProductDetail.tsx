'use client';
import { useState } from 'react';
import { formatPaise } from '@/lib/format';
import type { Product } from '@/lib/types';
import { Badges } from './Badges';
import { HeartIcon, TruckIcon } from './Icons';
import { ProductArt, type ArtView } from './ProductArt';
import { useWishlist } from './Providers';
import { useAddToCart } from './useAddToCart';

const VIEWS: [ArtView, string][] = [['drape', 'Full drape'], ['pallu', 'Pallu detail'], ['border', 'Border detail']];
const BULK_MIN = 10;

function Stepper({ value, min, max, onChange, label }: { value: number; min: number; max: number; onChange: (n: number) => void; label: string }) {
  return (
    <div className="inline-flex items-center rounded-full border border-line bg-white" role="group" aria-label={label}>
      <button type="button" className="h-11 w-11 rounded-full text-lg hover:bg-ivory-deep disabled:opacity-40" disabled={value <= min}
        aria-label="Decrease quantity" onClick={() => onChange(Math.max(min, value - 1))}>−</button>
      <input aria-label={label} inputMode="numeric" value={value} className="w-12 bg-transparent text-center font-semibold focus:outline-none"
        onChange={(e) => { const n = parseInt(e.target.value.replace(/\D/g, '') || '0', 10); onChange(Math.min(max, Math.max(min, n || min))); }} />
      <button type="button" className="h-11 w-11 rounded-full text-lg hover:bg-ivory-deep disabled:opacity-40" disabled={value >= max}
        aria-label="Increase quantity" onClick={() => onChange(Math.min(max, value + 1))}>+</button>
    </div>
  );
}

export function ProductDetail({ product, initialSku }: { product: Product; initialSku?: string }) {
  const start = Math.max(0, product.variants.findIndex((v) => v.sku === initialSku));
  const [active, setActive] = useState(start);
  const [view, setView] = useState<ArtView>('drape');
  const [qty, setQty] = useState(1);
  const [bulkQty, setBulkQty] = useState(BULK_MIN);
  const v = product.variants[active] ?? product.variants[0]!;
  const { add, pending, open } = useAddToCart();
  const { has, toggle } = useWishlist();
  const soldOut = v.inStock === false;
  const max = product.maxPerOrder ?? 5;

  return (
    <div className="grid gap-10 lg:grid-cols-[1.15fr_1fr] lg:gap-16">
      {/* Gallery */}
      <div className="lg:sticky lg:top-28 lg:self-start">
        <div className="overflow-hidden rounded-[24px] bg-ivory-deep">
          <ProductArt uid={`pdp-${v.sku}`} view={view} motif={product.motif} base={v.colorHex} accent={v.accentHex}
            className="aspect-[4/5] w-full" title={`${product.name} in ${v.colorName}, ${VIEWS.find(([k]) => k === view)?.[1]} (illustration)`} />
        </div>
        <div className="mt-4 grid grid-cols-3 gap-3" role="group" aria-label="Choose image">
          {VIEWS.map(([key, label]) => (
            <button key={key} type="button" onClick={() => setView(key)} aria-pressed={view === key} aria-label={label}
              className={`overflow-hidden rounded-xl ring-offset-2 ring-offset-ivory transition ${view === key ? 'ring-2 ring-plum' : 'opacity-80 hover:opacity-100'}`}>
              <ProductArt uid={`thumb-${v.sku}`} view={key} motif={product.motif} base={v.colorHex} accent={v.accentHex} className="aspect-[4/5] w-full" />
            </button>
          ))}
        </div>
        <p className="mt-3 text-xs text-muted">Illustrative artwork generated from the weave and colourway. Product photography to follow.</p>
      </div>

      {/* Details */}
      <div className="pb-24 lg:pb-0">
        <Badges product={product} />
        <p className="eyebrow mt-4">{product.collection.name}</p>
        <h1 className="mt-2 font-display text-4xl leading-tight text-plum sm:text-5xl">{product.name}</h1>
        <p className="mt-4 text-2xl font-semibold text-charcoal">{formatPaise(product.pricePaise)}</p>
        <p className="text-sm text-muted">Inclusive of GST. Shipping calculated at checkout.</p>

        <dl className="mt-6 grid grid-cols-3 gap-3 text-sm">
          {[['Fabric', product.fabric], ['Craft', product.craft], ['Occasion', product.occasion]].map(([k, val]) => (
            <div key={k} className="rounded-xl border border-line bg-white/60 px-3 py-2.5"><dt className="text-xs text-muted">{k}</dt><dd className="font-medium">{val}</dd></div>
          ))}
        </dl>

        <fieldset className="mt-8">
          <legend className="label">Colour: <span className="font-normal text-muted">{v.colorName}{soldOut ? ' — sold out' : ''}</span></legend>
          <div className="mt-2 flex flex-wrap gap-3">
            {product.variants.map((x, i) => (
              <button key={x.sku} type="button" onClick={() => setActive(i)} aria-pressed={i === active}
                aria-label={`${x.colorName}${x.inStock === false ? ' (sold out)' : ''}`}
                className={`relative h-11 w-11 rounded-full ring-offset-2 ring-offset-ivory transition ${i === active ? 'ring-2 ring-plum' : 'ring-1 ring-line hover:ring-plum/50'}`}
                style={{ background: `linear-gradient(135deg, ${x.colorHex} 60%, ${x.accentHex} 60%)` }}>
                {x.inStock === false && <span aria-hidden className="absolute inset-0 m-auto h-px w-full rotate-45 bg-charcoal/70" />}
              </button>
            ))}
          </div>
        </fieldset>

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <Stepper value={qty} min={1} max={max} onChange={setQty} label="Quantity" />
          <button type="button" className="btn-primary flex-1 py-3.5" disabled={!open || pending || soldOut} onClick={() => add(v.sku, qty)}>
            {!open ? 'Ordering opens soon' : soldOut ? 'Sold out in this colour' : pending ? 'Adding…' : 'Add to bag'}
          </button>
          <button type="button" onClick={() => toggle(product.slug)} aria-pressed={has(product.slug)}
            aria-label={has(product.slug) ? 'Remove from wishlist' : 'Save to wishlist'}
            className={`rounded-full border border-line p-3 hover:border-plum ${has(product.slug) ? 'text-danger' : 'text-plum'}`}>
            <HeartIcon filled={has(product.slug)} />
          </button>
        </div>
        <p className="mt-2 text-xs text-muted">{!open ? 'This is a catalogue preview — save this style to your wishlist and check back soon. ' : ''}Up to {max} per order for retail. Stock is confirmed when you add to bag and again at checkout.</p>

        {product.bulkEligible && product.bulkPricePaise ? (
          <section className="mt-8 rounded-[20px] border border-gold/50 bg-gradient-to-br from-white to-ivory-deep p-6" aria-labelledby="bulk-h">
            <h2 id="bulk-h" className="font-display text-2xl text-plum">Ordering for a group?</h2>
            <p className="mt-1 text-sm text-muted">
              Bulk price <strong className="text-charcoal">{formatPaise(product.bulkPricePaise)}</strong> per piece. Minimum {BULK_MIN} pieces across your bulk bag — mix colours and styles.
            </p>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <Stepper value={bulkQty} min={1} max={500} onChange={setBulkQty} label="Bulk quantity" />
              <button type="button" className="btn-outline flex-1" disabled={!open || pending || soldOut} onClick={() => add(v.sku, bulkQty, 'BULK')}>
                Add {bulkQty} to bulk order
              </button>
            </div>
          </section>
        ) : null}

        <div className="mt-8 flex items-start gap-3 rounded-xl bg-ivory-deep px-4 py-3.5 text-sm">
          <TruckIcon className="mt-0.5 shrink-0 text-gold-deep" />
          <p>Free shipping on orders over ₹2,999. Cash on delivery available on eligible PIN codes and order values.</p>
        </div>

        <div className="mt-8 divide-y divide-line border-y border-line">
          {[
            ['Description', product.description ?? ''],
            ['Care', 'Dry clean recommended for silks and zari. Handloom cottons and linens: gentle cold hand wash, dry in shade. Store folded in muslin; refold periodically.'],
            ['Shipping & returns', 'Dispatched in 2–4 working days. Returns accepted within 7 days of delivery on unworn items with tags. See our returns policy for details.'],
          ].map(([title, body], i) => (
            <details key={title} open={i === 0} className="group py-4">
              <summary className="flex cursor-pointer list-none items-center justify-between font-semibold">
                {title}<span aria-hidden className="text-xl text-gold-deep transition group-open:rotate-45">+</span>
              </summary>
              <p className="mt-3 leading-relaxed text-muted">{body}</p>
            </details>
          ))}
        </div>
      </div>

      {/* Sticky mobile add-to-cart */}
      <div className="fixed inset-x-0 bottom-0 z-30 flex items-center gap-3 border-t border-line bg-ivory/95 px-4 py-3 backdrop-blur lg:hidden">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{product.name}</p>
          <p className="text-sm text-plum">{formatPaise(product.pricePaise)} · {v.colorName}</p>
        </div>
        <button type="button" className="btn-primary px-5" disabled={!open || pending || soldOut} onClick={() => add(v.sku, qty)}>
          {!open ? 'Opening soon' : soldOut ? 'Sold out' : 'Add to bag'}
        </button>
      </div>
    </div>
  );
}
