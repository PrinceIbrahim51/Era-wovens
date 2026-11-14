import type { Metadata } from 'next';
import { Suspense } from 'react';
import { CatalogListing, ListingSkeleton, type SearchParams } from '@/components/CatalogListing';

export const metadata: Metadata = { title: 'Search' };

export default async function SearchPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const sp = await searchParams;
  const q = (Array.isArray(sp.q) ? sp.q[0] : sp.q)?.slice(0, 100) ?? '';
  return (
    <div className="container-x py-12">
      <header className="mb-12 max-w-2xl">
        <p className="eyebrow">Search</p>
        <h1 className="mt-3 font-display text-5xl text-plum">{q ? <>Results for “{q}”</> : 'Find your saree'}</h1>
        <form action="/search" role="search" className="mt-6 flex gap-2">
          <label htmlFor="q" className="sr-only">Search</label>
          <input id="q" name="q" type="search" defaultValue={q} maxLength={100} placeholder="Try “kanjivaram”, “linen” or “maroon”" className="field" />
          <button className="btn-primary">Search</button>
        </form>
      </header>
      {q ? (
        <Suspense key={JSON.stringify(sp)} fallback={<ListingSkeleton />}>
          <CatalogListing searchParams={sp} basePath="/search" />
        </Suspense>
      ) : null}
    </div>
  );
}
