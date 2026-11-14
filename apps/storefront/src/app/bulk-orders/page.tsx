import type { Metadata } from 'next';
import { Suspense } from 'react';
import { CatalogListing, ListingSkeleton, type SearchParams } from '@/components/CatalogListing';

export const metadata: Metadata = { title: 'Bulk orders', description: 'Bulk saree orders from 10 pieces — mix styles and colours with per-piece bulk pricing.' };

export default async function BulkOrdersPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const sp = await searchParams;
  return (
    <div className="container-x py-12">
      <header className="mb-12 grid gap-8 rounded-[26px] bg-plum p-8 text-ivory sm:p-12 md:grid-cols-[1.3fr_1fr]">
        <div>
          <p className="eyebrow text-gold">Bulk orders</p>
          <h1 className="mt-3 font-display text-5xl">Dress the whole celebration</h1>
          <p className="mt-4 max-w-xl text-ivory/85">For weddings, corporate gifting, events and boutiques. Bulk orders use their own bag and checkout, with per-piece bulk pricing shown on every eligible style.</p>
        </div>
        <ol className="grid gap-3 text-sm">
          {[
            ['1', 'Choose styles', 'Add any bulk-eligible styles and colours.'],
            ['2', 'Reach 10 pieces', 'Mix and match — the minimum counts across your bulk bag.'],
            ['3', 'Check out', 'Totals, GST and shipping are calculated securely at checkout.'],
          ].map(([n, t, d]) => (
            <li key={n} className="flex gap-4 rounded-xl bg-ivory/10 p-4">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold font-bold text-charcoal">{n}</span>
              <span><span className="block font-semibold">{t}</span><span className="text-ivory/75">{d}</span></span>
            </li>
          ))}
        </ol>
      </header>
      <Suspense key={JSON.stringify(sp)} fallback={<ListingSkeleton />}>
        <CatalogListing collection="bulk-orders" searchParams={sp} basePath="/bulk-orders" />
      </Suspense>
    </div>
  );
}
