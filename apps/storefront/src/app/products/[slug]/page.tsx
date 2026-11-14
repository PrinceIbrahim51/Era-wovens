import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ProductCard } from '@/components/ProductCard';
import { ProductDetail } from '@/components/ProductDetail';
import { getProduct, NotFoundError } from '@/lib/server-api';

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<{ color?: string }> };

async function load(slug: string) {
  if (!/^[a-z0-9-]{1,120}$/.test(slug)) notFound();
  try {
    return await getProduct(slug);
  } catch (e) {
    if (e instanceof NotFoundError) notFound();
    throw e;
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { product } = await load((await params).slug);
  return { title: product.name, description: product.description?.slice(0, 155) };
}

export default async function ProductPage({ params, searchParams }: Props) {
  const { slug } = await params;
  const { color } = await searchParams;
  const { product, related } = await load(slug);
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    description: product.description,
    material: product.fabric,
    sku: product.variants[0]?.sku,
    offers: product.variants.map((v) => ({
      '@type': 'Offer', sku: v.sku, priceCurrency: 'INR', price: (product.pricePaise / 100).toFixed(2),
      availability: v.inStock === false ? 'https://schema.org/OutOfStock' : 'https://schema.org/InStock',
    })),
  };
  return (
    <div className="container-x py-8">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }} />
      <nav aria-label="Breadcrumb" className="mb-8 text-sm text-muted">
        <ol className="flex flex-wrap items-center gap-2">
          <li><Link href="/" className="hover:text-plum">Home</Link></li><li aria-hidden>/</li>
          <li><Link href={`/collections/${product.collection.slug}`} className="hover:text-plum">{product.collection.name}</Link></li><li aria-hidden>/</li>
          <li aria-current="page" className="text-charcoal">{product.name}</li>
        </ol>
      </nav>
      <ProductDetail product={product} initialSku={color} />
      {related.length > 0 && (
        <section className="mt-24" aria-labelledby="related-heading">
          <h2 id="related-heading" className="font-display text-4xl text-plum">You may also like</h2>
          <ul className="mt-10 grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 lg:grid-cols-4">
            {related.map((p) => <li key={p.slug}><ProductCard product={p} /></li>)}
          </ul>
        </section>
      )}
    </div>
  );
}
