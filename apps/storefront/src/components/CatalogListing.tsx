import Link from 'next/link';
import { Suspense } from 'react';
import { getListing } from '@/lib/server-api';
import type { Listing } from '@/lib/types';
import { Filters, SortSelect } from './Filters';
import { ProductCard } from './ProductCard';

export type SearchParams = Record<string, string | string[] | undefined>;
const one = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

export async function CatalogListing({ collection, searchParams, basePath }: { collection?: string; searchParams: SearchParams; basePath: string }) {
  const params: Record<string, string | undefined> = {
    collection,
    q: one(searchParams.q)?.slice(0, 100),
    color: one(searchParams.color),
    fabric: one(searchParams.fabric),
    minPrice: one(searchParams.minPrice)?.replace(/\D/g, ''),
    maxPrice: one(searchParams.maxPrice)?.replace(/\D/g, ''),
    inStock: one(searchParams.inStock) === 'true' ? 'true' : undefined,
    sort: one(searchParams.sort),
    page: one(searchParams.page)?.replace(/\D/g, '') || undefined,
    size: '12',
  };
  let listing: Listing;
  try {
    listing = await getListing(params);
  } catch {
    return (
      <div className="card mx-auto my-16 max-w-lg p-10 text-center">
        <h2 className="font-display text-3xl text-plum">We couldn’t load the catalogue</h2>
        <p className="mt-3 text-muted">This is usually temporary. Please refresh in a moment.</p>
        <Link href={basePath} className="btn-primary mt-6">Try again</Link>
      </div>
    );
  }
  const bulk = listing.collection?.bulk ?? false;
  const qs = (page: number) => {
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) if (v && k !== 'collection' && k !== 'size' && k !== 'page') p.set(k, v);
    if (page > 1) p.set('page', String(page));
    const s = p.toString();
    return s ? `${basePath}?${s}` : basePath;
  };

  return (
    <div className="grid gap-10 lg:grid-cols-[15rem_1fr]">
      <Suspense><Filters facets={listing.facets} total={listing.total} /></Suspense>
      <section aria-label="Products">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3 lg:-mt-12">
          <p className="text-sm text-muted" aria-live="polite">{listing.total} {listing.total === 1 ? 'style' : 'styles'}</p>
          <Suspense><SortSelect /></Suspense>
        </div>
        {!listing.availabilityKnown && (
          <p className="mb-6 rounded-lg bg-ivory-deep px-4 py-3 text-sm text-muted">Live stock is temporarily unavailable; availability will be confirmed in your bag.</p>
        )}
        {listing.items.length === 0 ? (
          <div className="card px-8 py-16 text-center">
            <h2 className="font-display text-3xl text-plum">No styles match these filters</h2>
            <p className="mt-3 text-muted">Try removing a filter or searching for a different fabric or colour.</p>
            <Link href={basePath} className="btn-outline mt-6">Clear filters</Link>
          </div>
        ) : (
          <ul className="grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 md:grid-cols-3">
            {listing.items.map((p) => <li key={p.slug}><ProductCard product={p} bulkContext={bulk} /></li>)}
          </ul>
        )}
        {listing.totalPages > 1 && (
          <nav aria-label="Pagination" className="mt-14 flex items-center justify-center gap-2">
            {Array.from({ length: listing.totalPages }, (_, i) => i + 1).map((n) => (
              <Link key={n} href={qs(n)} aria-current={n === listing.page ? 'page' : undefined}
                className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold ${n === listing.page ? 'bg-plum text-ivory' : 'hover:bg-ivory-deep'}`}>{n}</Link>
            ))}
          </nav>
        )}
      </section>
    </div>
  );
}

export function ListingSkeleton() {
  return (
    <div className="grid gap-10 lg:grid-cols-[15rem_1fr]" aria-busy="true" aria-label="Loading products">
      <div className="hidden space-y-4 lg:block">{[0, 1, 2].map((i) => <div key={i} className="skeleton h-24" />)}</div>
      <ul className="grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 md:grid-cols-3">
        {Array.from({ length: 6 }, (_, i) => (
          <li key={i}><div className="skeleton aspect-[4/5] rounded-[18px]" /><div className="skeleton mt-4 h-4 w-2/3" /><div className="skeleton mt-2 h-4 w-1/3" /></li>
        ))}
      </ul>
    </div>
  );
}
