import type { Metadata } from 'next';
import { Suspense } from 'react';
import { CartView } from './CartView';

export const metadata: Metadata = { title: 'Your bag' };

export default function CartPage() {
  return (
    <div className="container-x py-12">
      <Suspense><CartView /></Suspense>
    </div>
  );
}
