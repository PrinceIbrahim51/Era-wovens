'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ProductCard } from '@/components/ProductCard';
import { useWishlist } from '@/components/Providers';
import { api } from '@/lib/api';
import type { Product } from '@/lib/types';

export default function WishlistPage() {
  const { slugs } = useWishlist();
  const [products, setProducts] = useState<Product[] | null>(null);
  useEffect(() => { document.title = 'Wishlist · Era Wovens'; }, []);
  useEffect(() => {
    if (!slugs.length) { setProducts([]); return; }
    api<Product[]>(`/api/v1/catalog/products/batch?slugs=${encodeURIComponent(slugs.join(','))}`)
      .then((list) => setProducts(slugs.map((s) => list.find((p) => p.slug === s)).filter((p): p is Product => !!p)))
      .catch(() => setProducts([]));
  }, [slugs]);
  return (
    <div className="container-x py-12">
      <h1 className="font-display text-5xl text-plum">Wishlist</h1>
      <p className="mt-3 text-muted">Saved on this device.</p>
      {products === null ? (
        <div className="mt-10 grid grid-cols-2 gap-6 md:grid-cols-4" aria-busy="true">{[0, 1, 2, 3].map((i) => <div key={i} className="skeleton aspect-[4/5]" />)}</div>
      ) : products.length === 0 ? (
        <div className="card mt-10 p-12 text-center"><p className="text-muted">Tap the heart on any saree to save it here.</p><Link href="/collections/all" className="btn-primary mt-6">Browse sarees</Link></div>
      ) : (
        <ul className="mt-10 grid grid-cols-2 gap-x-4 gap-y-10 sm:gap-x-6 md:grid-cols-3 lg:grid-cols-4">
          {products.map((p) => <li key={p.slug}><ProductCard product={p} /></li>)}
        </ul>
      )}
    </div>
  );
}
