import type { Metadata } from 'next';
import { Suspense } from 'react';
import { OrderDetail } from './OrderDetail';

export const metadata: Metadata = { title: 'Your order' };

export default async function OrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <div className="container-x py-12">
      <Suspense><OrderDetail id={id} /></Suspense>
    </div>
  );
}
