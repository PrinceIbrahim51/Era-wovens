import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Suspense } from 'react';
import { CatalogListing, ListingSkeleton, type SearchParams } from '@/components/CatalogListing';
import { getCollections } from '@/lib/server-api';

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<SearchParams> };

async function resolve(slug: string) {
  if (slug === 'all') return { slug: 'all', name: 'All sarees', description: 'Every style across our collections.', bulk: false };
  const all = await getCollections().catch(() => []);
  return all.find((c) => c.slug === slug) ?? null;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const c = await resolve((await params).slug);
  return { title: c?.name ?? 'Collection', description: c?.description };
}

export default async function CollectionPage({ params, searchParams }: Props) {
  const { slug } = await params;
  if (!/^[a-z0-9-]{1,80}$/.test(slug)) notFound();
  const c = await resolve(slug);
  if (!c) notFound();
  const sp = await searchParams;
  return (
    <div className="container-x py-12">
      <header className="mb-12 max-w-2xl">
        <p className="eyebrow">{c.bulk ? 'Bulk' : 'Collection'}</p>
        <h1 className="mt-3 font-display text-5xl text-plum">{c.name}</h1>
        <p className="mt-4 text-muted">{c.description}</p>
      </header>
      <Suspense key={JSON.stringify(sp)} fallback={<ListingSkeleton />}>
        <CatalogListing collection={slug === 'all' ? undefined : slug} searchParams={sp} basePath={`/collections/${slug}`} />
      </Suspense>
    </div>
  );
}
