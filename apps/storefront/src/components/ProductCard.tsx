'use client';
import Link from 'next/link';
import { useRef, useState } from 'react';
import { formatPaise } from '@/lib/format';
import type { Product } from '@/lib/types';
import { Badges } from './Badges';
import { CloseIcon, EyeIcon, HeartIcon } from './Icons';
import { ProductArt } from './ProductArt';
import { useWishlist } from './Providers';
import { useAddToCart } from './useAddToCart';

export function ProductCard({ product, bulkContext = false }: { product: Product; bulkContext?: boolean }) {
  const [active, setActive] = useState(0);
  const variant = product.variants[active] ?? product.variants[0];
  const { has, toggle } = useWishlist();
  const wished = has(product.slug);
  const quick = useRef<HTMLDialogElement>(null);
  const href = `/products/${product.slug}${active ? `?color=${encodeURIComponent(variant?.sku ?? '')}` : ''}`;
  const price = bulkContext && product.bulkPricePaise ? product.bulkPricePaise : product.pricePaise;

  if (!variant) return null;
  return (
    <article className="group relative flex flex-col" aria-labelledby={`p-${product.slug}`}>
      <div className="relative overflow-hidden rounded-[18px] bg-ivory-deep">
        <Link href={href} tabIndex={-1} aria-hidden className="block">
          <div className="relative aspect-[4/5]">
            <ProductArt uid={`c-${product.slug}-${variant.sku}`} motif={product.motif} base={variant.colorHex} accent={variant.accentHex}
              className="absolute inset-0 h-full w-full transition duration-700 ease-[var(--ease-luxe)] motion-safe:group-hover:scale-[1.06]" />
            <ProductArt uid={`c2-${product.slug}-${variant.sku}`} view="pallu" motif={product.motif} base={variant.colorHex} accent={variant.accentHex}
              className="absolute inset-0 h-full w-full opacity-0 transition duration-500 group-hover:opacity-100" />
          </div>
        </Link>
        <Badges product={product} className="absolute left-3 right-14 top-3 [&>li:nth-child(n+2)]:max-sm:hidden" />
        <button type="button" onClick={() => toggle(product.slug)} aria-pressed={wished}
          aria-label={wished ? `Remove ${product.name} from wishlist` : `Save ${product.name} to wishlist`}
          className={`absolute right-3 top-3 rounded-full bg-ivory/90 p-2 shadow-sm transition hover:scale-105 ${wished ? 'text-danger' : 'text-plum'}`}>
          <HeartIcon filled={wished} width={18} height={18} />
        </button>
        <button type="button" onClick={() => quick.current?.showModal()}
          className="absolute inset-x-3 bottom-3 flex items-center justify-center gap-2 rounded-full bg-ivory/95 py-2.5 text-xs font-semibold uppercase tracking-wider text-plum opacity-0 shadow-lg transition duration-300 focus:opacity-100 group-hover:opacity-100 max-lg:hidden">
          <EyeIcon width={16} height={16} /> Quick view
        </button>
      </div>

      <div className="mt-4 flex flex-1 flex-col">
        <p className="text-[0.7rem] font-semibold uppercase tracking-[0.18em] text-muted">{product.fabric} · {product.craft}</p>
        <h3 id={`p-${product.slug}`} className="mt-1 font-display text-[1.35rem] leading-snug text-charcoal">
          <Link href={href} className="hover:text-plum">{product.name}</Link>
        </h3>
        <p className="mt-1 text-[0.95rem] font-semibold text-plum">
          {formatPaise(price)}
          {bulkContext && product.bulkPricePaise ? <span className="ml-1.5 text-xs font-medium text-muted">/ piece in bulk · retail {formatPaise(product.pricePaise)}</span> : null}
        </p>
        <div className="mt-3 flex items-center gap-1.5" role="group" aria-label={`Colours for ${product.name}`}>
          {product.variants.slice(0, 5).map((v, i) => (
            <button key={v.sku} type="button" onClick={() => setActive(i)} onMouseEnter={() => setActive(i)}
              aria-label={`${v.colorName}${v.inStock === false ? ' (sold out)' : ''}`} aria-pressed={i === active}
              className={`relative h-6 w-6 rounded-full ring-offset-2 ring-offset-ivory transition ${i === active ? 'ring-2 ring-plum' : 'ring-1 ring-line hover:ring-plum/50'}`}
              style={{ background: `linear-gradient(135deg, ${v.colorHex} 60%, ${v.accentHex} 60%)` }}>
              {v.inStock === false && <span aria-hidden className="absolute inset-0 m-auto h-px w-full rotate-45 bg-charcoal/70" />}
            </button>
          ))}
          {product.variants.length > 5 && <span className="text-xs text-muted">+{product.variants.length - 5}</span>}
        </div>
      </div>

      <QuickView ref={quick} product={product} initial={active} bulkContext={bulkContext} />
    </article>
  );
}

function QuickView({ product, initial, bulkContext, ref }: { product: Product; initial: number; bulkContext: boolean; ref: React.Ref<HTMLDialogElement> }) {
  const [active, setActive] = useState(initial);
  const v = product.variants[active] ?? product.variants[0]!;
  const { add, pending } = useAddToCart();
  const bulk = bulkContext && product.bulkEligible;
  const qty = bulk ? 10 : 1;
  return (
    <dialog ref={ref} aria-labelledby={`qv-${product.slug}`}
      className="m-auto w-[min(56rem,94vw)] max-w-none overflow-hidden rounded-[22px] bg-ivory p-0 text-charcoal shadow-2xl"
      onClick={(e) => { if (e.target === e.currentTarget) e.currentTarget.close(); }}>
      <div className="grid md:grid-cols-2">
        <div className="bg-ivory-deep">
          <ProductArt uid={`qv-${product.slug}-${v.sku}`} motif={product.motif} base={v.colorHex} accent={v.accentHex} className="aspect-[4/5] w-full" title={`${product.name} in ${v.colorName} (illustration)`} />
        </div>
        <div className="relative flex flex-col p-7 sm:p-9">
          <form method="dialog" className="absolute right-4 top-4">
            <button aria-label="Close quick view" className="rounded-full p-2 hover:bg-ivory-deep"><CloseIcon /></button>
          </form>
          <p className="eyebrow">{product.collection.name}</p>
          <h2 id={`qv-${product.slug}`} className="mt-2 font-display text-3xl text-plum">{product.name}</h2>
          <p className="mt-2 text-lg font-semibold">{formatPaise(bulk && product.bulkPricePaise ? product.bulkPricePaise : product.pricePaise)}{bulk ? <span className="text-sm font-normal text-muted"> / piece</span> : null}</p>
          <p className="mt-1 text-sm text-muted">{product.fabric} · {product.craft} · {product.occasion}</p>
          <fieldset className="mt-6">
            <legend className="label">Colour: <span className="font-normal text-muted">{v.colorName}</span></legend>
            <div className="flex flex-wrap gap-2">
              {product.variants.map((x, i) => (
                <button key={x.sku} type="button" onClick={() => setActive(i)} aria-pressed={i === active} aria-label={x.colorName}
                  className={`h-9 w-9 rounded-full ring-offset-2 ${i === active ? 'ring-2 ring-plum' : 'ring-1 ring-line'}`}
                  style={{ background: `linear-gradient(135deg, ${x.colorHex} 60%, ${x.accentHex} 60%)` }} />
              ))}
            </div>
          </fieldset>
          <div className="mt-auto flex flex-col gap-3 pt-8">
            <button type="button" className="btn-primary" disabled={pending || v.inStock === false}
              onClick={async () => { if (await add(v.sku, qty, bulk ? 'BULK' : 'REGULAR')) (ref as React.RefObject<HTMLDialogElement>)?.current?.close(); }}>
              {v.inStock === false ? 'Sold out in this colour' : pending ? 'Adding…' : bulk ? `Add ${qty} to bulk order` : 'Add to bag'}
            </button>
            <Link href={`/products/${product.slug}`} className="btn-outline">View full details</Link>
          </div>
        </div>
      </div>
    </dialog>
  );
}
