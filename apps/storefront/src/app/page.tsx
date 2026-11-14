import Link from 'next/link';
import { ProductArt } from '@/components/ProductArt';
import { ProductCard } from '@/components/ProductCard';
import { BoxIcon, LoomIcon, ShieldIcon, TruckIcon } from '@/components/Icons';
import { getCollections, getListing } from '@/lib/server-api';
import type { Collection, Product } from '@/lib/types';

const COLLECTION_ART: Record<string, { motif: 'temple' | 'jaal' | 'checks' | 'floral'; base: string; accent: string }> = {
  'kanjivaram-silks': { motif: 'temple', base: '#6B1E2A', accent: '#C9A55C' },
  'banarasi-weaves': { motif: 'jaal', base: '#0F4C55', accent: '#CFAE62' },
  'handloom-cottons': { motif: 'checks', base: '#2F3F73', accent: '#E7DCC2' },
  'linen-organza': { motif: 'floral', base: '#B5A1C9', accent: '#F2E9F0' },
};

export default async function Home() {
  let collections: Collection[] = [];
  let newest: Product[] = [];
  let featured: Product[] = [];
  try {
    [collections, newest, featured] = await Promise.all([
      getCollections(),
      getListing({ sort: 'newest', size: '4' }).then((l) => l.items),
      getListing({ sort: 'featured', size: '8' }).then((l) => l.items.slice(4, 8)),
    ]);
  } catch {
    // Render editorial content even if the catalogue is down.
  }

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden">
        <div aria-hidden className="absolute inset-0 bg-[radial-gradient(60rem_30rem_at_85%_10%,rgb(215_182_174/0.45),transparent),radial-gradient(40rem_30rem_at_0%_100%,rgb(197_164_109/0.18),transparent)]" />
        <div className="container-x relative grid items-center gap-12 py-14 md:grid-cols-[1.05fr_1fr] md:py-24">
          <div className="motion-safe:animate-[fadeup_.8s_ease-out]">
            <p className="eyebrow">The Festive Edit · 2026</p>
            <h1 className="mt-5 font-display text-5xl font-semibold leading-[1.02] text-plum sm:text-6xl lg:text-7xl">
              Woven for the moments <em className="font-medium text-gold-deep">that matter.</em>
            </h1>
            <p className="mt-6 max-w-md text-lg leading-relaxed text-muted">
              Kanjivaram and Banarasi silks, breathable handloom cottons and airy organzas — chosen for weddings, festivals and every gathering in between.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link href="/collections/all" className="btn-primary">Shop sarees</Link>
              <Link href="/bulk-orders" className="btn-outline">Bulk orders from 10 pieces</Link>
            </div>
          </div>
          <div className="relative mx-auto aspect-[5/5] w-full max-w-lg" aria-hidden>
            <div className="absolute right-6 top-0 h-40 w-40 rounded-full border border-gold/60" />
            <div className="absolute left-[4%] top-[12%] w-[46%] -rotate-6 overflow-hidden rounded-[22px] shadow-[0_30px_60px_-25px_rgb(53_36_51/0.55)]">
              <ProductArt uid="hero-1" motif="jaal" base="#5A1730" accent="#C7A65E" className="aspect-[4/5] w-full" />
            </div>
            <div className="absolute right-[4%] top-[4%] z-10 w-[52%] rotate-3 overflow-hidden rounded-[22px] shadow-[0_40px_80px_-30px_rgb(53_36_51/0.6)]">
              <ProductArt uid="hero-2" motif="temple" base="#0F5A4A" accent="#D4B16A" className="aspect-[4/5] w-full" />
            </div>
            <div className="absolute bottom-0 left-[22%] z-20 w-[44%] -rotate-2 overflow-hidden rounded-[22px] shadow-[0_30px_60px_-25px_rgb(53_36_51/0.55)]">
              <ProductArt uid="hero-3" view="pallu" motif="paisley" base="#C2413A" accent="#E7CB8A" className="aspect-[4/5] w-full" />
            </div>
          </div>
        </div>
      </section>

      {/* Trust strip */}
      <section aria-label="Why shop with us" className="border-y border-line bg-white/50">
        <ul className="container-x grid grid-cols-2 gap-6 py-7 text-sm md:grid-cols-4">
          {[
            [LoomIcon, 'Handwoven & handloom', 'Craft details on every product'],
            [TruckIcon, 'Free shipping over ₹2,999', 'Across serviceable PIN codes'],
            [BoxIcon, 'Cash on delivery', 'On eligible orders'],
            [ShieldIcon, 'Secure checkout', 'Prices verified server-side'],
          ].map(([Icon, title, sub]) => {
            const I = Icon as typeof LoomIcon;
            return (
              <li key={title as string} className="flex items-start gap-3">
                <I className="mt-0.5 shrink-0 text-gold-deep" />
                <span><span className="block font-semibold text-charcoal">{title as string}</span><span className="text-muted">{sub as string}</span></span>
              </li>
            );
          })}
        </ul>
      </section>

      {/* Collections */}
      <section className="container-x py-20" aria-labelledby="collections-heading">
        <div className="flex items-end justify-between gap-6">
          <div>
            <p className="eyebrow">Collections</p>
            <h2 id="collections-heading" className="mt-3 font-display text-4xl text-plum sm:text-5xl">Four traditions, one wardrobe</h2>
          </div>
          <Link href="/collections/all" className="hidden text-sm font-semibold text-gold-deep underline-offset-4 hover:underline sm:block">View all →</Link>
        </div>
        <ul className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {collections.filter((c) => !c.bulk).map((c, i) => {
            const art = COLLECTION_ART[c.slug] ?? COLLECTION_ART['kanjivaram-silks']!;
            return (
              <li key={c.slug}>
                <Link href={`/collections/${c.slug}`} className="group relative block overflow-hidden rounded-[22px]">
                  <ProductArt uid={`col-${c.slug}`} view={i % 2 ? 'border' : 'drape'} {...art} className="aspect-[4/5] w-full transition duration-700 motion-safe:group-hover:scale-105" />
                  <div className="absolute inset-0 bg-gradient-to-t from-charcoal/80 via-charcoal/10 to-transparent" />
                  <div className="absolute inset-x-0 bottom-0 p-6 text-ivory">
                    <h3 className="font-display text-3xl">{c.name}</h3>
                    <p className="mt-1 text-sm text-ivory/85 line-clamp-2">{c.description}</p>
                  </div>
                </Link>
              </li>
            );
          })}
        </ul>
      </section>

      {/* New arrivals */}
      {newest.length > 0 && (
        <section className="container-x pb-20" aria-labelledby="new-heading">
          <p className="eyebrow">Just arrived</p>
          <h2 id="new-heading" className="mt-3 font-display text-4xl text-plum">New on the loom</h2>
          <ul className="mt-10 grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {newest.map((p) => <li key={p.slug}><ProductCard product={p} /></li>)}
          </ul>
        </section>
      )}

      {/* Bulk promo */}
      <section className="bg-plum text-ivory" aria-labelledby="bulk-heading">
        <div className="container-x grid items-center gap-12 py-20 md:grid-cols-2">
          <div>
            <p className="eyebrow text-gold">For weddings, teams & boutiques</p>
            <h2 id="bulk-heading" className="mt-4 font-display text-4xl leading-tight sm:text-5xl">Bulk orders, made simple</h2>
            <ul className="mt-8 space-y-4 text-ivory/85">
              <li className="flex gap-3"><span className="text-gold">◆</span> Minimum 10 pieces per order — mix styles and colours freely.</li>
              <li className="flex gap-3"><span className="text-gold">◆</span> Dedicated per-piece bulk pricing on eligible styles.</li>
              <li className="flex gap-3"><span className="text-gold">◆</span> A separate bulk bag, so retail and bulk orders never mix.</li>
            </ul>
            <Link href="/bulk-orders" className="btn mt-10 bg-gold text-charcoal hover:bg-ivory">Explore the bulk collection</Link>
          </div>
          <div className="grid grid-cols-3 gap-3" aria-hidden>
            {[['#9E1B32', '#D8B56A'], ['#24438C', '#D8B56A'], ['#E08A1E', '#7D1F2A'], ['#1D4E3A', '#D8B56A'], ['#6E1F2E', '#D2B06A'], ['#16626B', '#E2C98A']].map(([b, a], i) => (
              <div key={i} className={`overflow-hidden rounded-xl ${i % 2 ? 'translate-y-6' : ''}`}>
                <ProductArt uid={`bulk-${i}`} view="border" motif={i % 2 ? 'paisley' : 'stripes'} base={b!} accent={a!} className="aspect-[4/5] w-full" />
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Craft story */}
      <section className="container-x grid items-center gap-12 py-24 md:grid-cols-2" aria-labelledby="craft-heading">
        <div className="overflow-hidden rounded-[26px]">
          <ProductArt uid="craft" view="border" motif="temple" base="#6B1E2A" accent="#C9A55C" className="aspect-[5/4] w-full" title="Illustration of a temple-motif zari border" />
        </div>
        <div>
          <p className="eyebrow">Our craft</p>
          <h2 id="craft-heading" className="mt-3 font-display text-4xl text-plum sm:text-5xl">Every border tells where it was woven</h2>
          <p className="mt-6 leading-relaxed text-muted">
            Temple spires from the south, trellis jaals from the north, quiet checks from everyday looms. We describe fabric, weave and craft plainly on every product, so you know exactly what you are choosing.
          </p>
          <Link href="/about" className="btn-outline mt-8">Read our story</Link>
        </div>
      </section>

      {featured.length > 0 && (
        <section className="container-x" aria-labelledby="picks-heading">
          <p className="eyebrow">Editor’s picks</p>
          <h2 id="picks-heading" className="mt-3 font-display text-4xl text-plum">Chosen for the season</h2>
          <ul className="mt-10 grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {featured.map((p) => <li key={p.slug}><ProductCard product={p} /></li>)}
          </ul>
        </section>
      )}
    </>
  );
}
