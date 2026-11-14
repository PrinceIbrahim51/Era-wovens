import type { Metadata } from 'next';
import { Suspense } from 'react';
import { CheckoutForm } from './CheckoutForm';

export const metadata: Metadata = { title: 'Checkout' };

export default function CheckoutPage() {
  return (
    <div className="container-x py-12">
      <Suspense><CheckoutForm /></Suspense>
    </div>
  );
}
