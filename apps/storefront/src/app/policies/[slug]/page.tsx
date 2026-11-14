import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Prose } from '@/components/Prose';

const POLICIES: Record<string, { title: string; body: React.ReactNode }> = {
  shipping: {
    title: 'Shipping policy',
    body: (<>
      <p>We ship across serviceable Indian PIN codes. Orders are dispatched within 2–4 working days.</p>
      <ul><li>Retail orders: free shipping from ₹2,999 (after discounts); otherwise ₹99.</li><li>Bulk orders: free shipping from ₹25,000; otherwise ₹499.</li></ul>
      <p>Your exact shipping charge is calculated at checkout before you pay.</p>
    </>),
  },
  returns: {
    title: 'Returns & refunds',
    body: (<>
      <p>Unworn items with original tags may be returned within 7 days of delivery. Bulk orders and customised pieces may have different terms, confirmed before purchase.</p>
      <p>Refunds for online payments are issued to the original payment method. COD refunds are issued by bank transfer after the return is received.</p>
    </>),
  },
  privacy: {
    title: 'Privacy policy',
    body: (<>
      <p>We collect only what we need to deliver your order: your name, email, mobile number and delivery address. We do not sell personal data.</p>
      <p>Payments are processed by Razorpay; we never see or store full card numbers or CVV. A first-party, HttpOnly cookie keeps your bag and orders together on this device. Your wishlist is stored only in your browser.</p>
      <p>Retention and deletion periods will be defined with legal advice before launch.</p>
    </>),
  },
  terms: {
    title: 'Terms of use',
    body: (<>
      <p>This is a preview storefront with a sample catalogue. Orders placed here are for demonstration and are not fulfilled. Online payments run in Razorpay Test Mode.</p>
      <p>Final terms covering sale, delivery, liability and governing law will be published before launch.</p>
    </>),
  },
};

export function generateStaticParams() { return Object.keys(POLICIES).map((slug) => ({ slug })); }

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  return { title: POLICIES[(await params).slug]?.title ?? 'Policy' };
}

export default async function PolicyPage({ params }: { params: Promise<{ slug: string }> }) {
  const p = POLICIES[(await params).slug];
  if (!p) notFound();
  return <Prose eyebrow="Policies" title={p.title} draft>{p.body}</Prose>;
}
