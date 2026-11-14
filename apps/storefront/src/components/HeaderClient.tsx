'use client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useId, useRef, useState } from 'react';
import type { Collection } from '@/lib/types';
import { BagIcon, CloseIcon, HeartIcon, MenuIcon, SearchIcon } from './Icons';
import { ProductArt } from './ProductArt';
import { useCartSummary, useWishlist } from './Providers';

const FEATURE_ART: Record<string, { motif: 'temple' | 'jaal' | 'checks' | 'floral' | 'stripes'; base: string; accent: string }> = {
  'kanjivaram-silks': { motif: 'temple', base: '#6B1E2A', accent: '#C9A55C' },
  'banarasi-weaves': { motif: 'jaal', base: '#0F4C55', accent: '#CFAE62' },
  'handloom-cottons': { motif: 'checks', base: '#2F3F73', accent: '#E7DCC2' },
  'linen-organza': { motif: 'floral', base: '#B5A1C9', accent: '#F2E9F0' },
  'bulk-orders': { motif: 'stripes', base: '#9E1B32', accent: '#D8B56A' },
};

export function HeaderClient({ collections }: { collections: Collection[] }) {
  const { summary } = useCartSummary();
  const { slugs } = useWishlist();
  const [megaOpen, setMegaOpen] = useState(false);
  const [hovered, setHovered] = useState<string>(collections[0]?.slug ?? 'kanjivaram-silks');
  const drawer = useRef<HTMLDialogElement>(null);
  const megaId = useId();
  const pathname = usePathname();
  const router = useRouter();
  const units = summary.regularUnits + summary.bulkUnits;

  useEffect(() => { setMegaOpen(false); drawer.current?.close(); }, [pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMegaOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const onSearch = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const q = String(new FormData(e.currentTarget).get('q') ?? '').trim();
    if (q) router.push(`/search?q=${encodeURIComponent(q)}`);
  };
  const art = FEATURE_ART[hovered] ?? FEATURE_ART['kanjivaram-silks']!;
  const regular = collections.filter((c) => !c.bulk);

  return (
    <>
      <nav aria-label="Primary" className="hidden flex-1 items-center gap-1 lg:flex">
        <div className="relative" onMouseLeave={() => setMegaOpen(false)}>
          <button type="button" aria-expanded={megaOpen} aria-controls={megaId} onClick={() => setMegaOpen((o) => !o)}
            onMouseEnter={() => setMegaOpen(true)} className="btn-ghost px-4 py-2">
            Shop sarees
          </button>
          <div id={megaId} hidden={!megaOpen}
            className="absolute left-0 top-full z-50 mt-2 w-[44rem] rounded-2xl border border-line bg-ivory p-6 shadow-[0_30px_80px_-30px_rgb(53_36_51/0.45)]">
            <div className="grid grid-cols-[1fr_15rem] gap-8">
              <div>
                <p className="eyebrow mb-4">Collections</p>
                <ul className="space-y-1">
                  {regular.map((c) => (
                    <li key={c.slug}>
                      <Link href={`/collections/${c.slug}`} onMouseEnter={() => setHovered(c.slug)} onFocus={() => setHovered(c.slug)}
                        className="group block rounded-lg px-3 py-2.5 hover:bg-ivory-deep">
                        <span className="font-display text-xl text-plum">{c.name}</span>
                        <span className="mt-0.5 block text-sm text-muted line-clamp-1">{c.description}</span>
                      </Link>
                    </li>
                  ))}
                  <li><Link href="/collections/all" className="block px-3 py-2.5 text-sm font-semibold text-gold-deep hover:underline">View all sarees →</Link></li>
                </ul>
              </div>
              <div className="overflow-hidden rounded-xl">
                <ProductArt uid={`mega-${hovered}`} {...art} className="aspect-[4/5] w-full" title="" />
              </div>
            </div>
          </div>
        </div>
        <Link href="/bulk-orders" className="btn-ghost px-4 py-2">Bulk orders</Link>
        <Link href="/collections/all?sort=newest" className="btn-ghost px-4 py-2">New in</Link>
        <Link href="/about" className="btn-ghost px-4 py-2">Our craft</Link>
      </nav>

      <form role="search" onSubmit={onSearch} className="ml-auto hidden md:block">
        <label htmlFor="site-search" className="sr-only">Search sarees</label>
        <div className="relative">
          <SearchIcon className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" width={18} height={18} />
          <input id="site-search" name="q" type="search" maxLength={100} placeholder="Search silk, cotton, maroon…"
            className="w-56 rounded-full border border-line bg-white/80 py-2 pl-9 pr-4 text-sm focus:w-72 focus:border-plum focus:outline-none transition-all duration-300" />
        </div>
      </form>

      <div className="ml-auto flex items-center gap-1 md:ml-2">
        <Link href="/search" className="rounded-full p-2.5 hover:bg-ivory-deep md:hidden" aria-label="Search"><SearchIcon /></Link>
        <Link href="/wishlist" className="relative rounded-full p-2.5 hover:bg-ivory-deep" aria-label={`Wishlist, ${slugs.length} items`}>
          <HeartIcon />
          {slugs.length > 0 && <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-gold" aria-hidden />}
        </Link>
        <Link href="/cart" className="relative rounded-full p-2.5 hover:bg-ivory-deep" aria-label={`Cart, ${units} items`}>
          <BagIcon />
          {units > 0 && (
            <span aria-hidden className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-plum px-1 text-[0.65rem] font-bold text-ivory">
              {units > 99 ? '99+' : units}
            </span>
          )}
        </Link>
        <button type="button" className="rounded-full p-2.5 hover:bg-ivory-deep lg:hidden" aria-label="Open menu"
          onClick={() => drawer.current?.showModal()}>
          <MenuIcon />
        </button>
      </div>

      <dialog ref={drawer} aria-label="Menu"
        className="m-0 ml-auto h-dvh max-h-none w-[min(22rem,88vw)] max-w-none bg-ivory p-0 text-charcoal shadow-2xl open:animate-[slidein_.3s_ease-out]">
        <div className="flex items-center justify-between border-b border-line px-5 py-4">
          <span className="font-display text-2xl text-plum">Menu</span>
          <button type="button" aria-label="Close menu" className="rounded-full p-2 hover:bg-ivory-deep" onClick={() => drawer.current?.close()}><CloseIcon /></button>
        </div>
        <form role="search" onSubmit={onSearch} className="px-5 pt-5">
          <label htmlFor="drawer-search" className="sr-only">Search sarees</label>
          <input id="drawer-search" name="q" type="search" maxLength={100} placeholder="Search sarees" className="field" />
        </form>
        <nav aria-label="Mobile" className="px-3 py-4">
          <ul className="space-y-1">
            {regular.map((c) => (
              <li key={c.slug}><Link className="block rounded-lg px-3 py-3 font-display text-xl text-plum hover:bg-ivory-deep" href={`/collections/${c.slug}`}>{c.name}</Link></li>
            ))}
            <li><Link className="block rounded-lg px-3 py-3 font-display text-xl text-plum hover:bg-ivory-deep" href="/bulk-orders">Bulk orders</Link></li>
            <li className="zari-rule my-3" aria-hidden />
            {[['/collections/all', 'All sarees'], ['/orders', 'My orders'], ['/track', 'Track an order'], ['/about', 'Our craft'], ['/faq', 'Help & FAQ'], ['/contact', 'Contact']].map(([href, label]) => (
              <li key={href}><Link className="block rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-ivory-deep" href={href!}>{label}</Link></li>
            ))}
          </ul>
        </nav>
      </dialog>
    </>
  );
}
