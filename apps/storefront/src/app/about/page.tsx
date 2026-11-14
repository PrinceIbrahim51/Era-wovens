import type { Metadata } from 'next';
import Image from 'next/image';
import logo from '../../../public/brand/era-wovens-badge.png';
import { Prose } from '@/components/Prose';

export const metadata: Metadata = { title: 'Our craft' };

export default function About() {
  return (
    <>
      <div className="container-x mt-12 flex justify-center"><Image src={logo} alt="Era Wovens — Made for last" width={260} height={260} className="drop-shadow-[0_12px_40px_rgb(197_164_109/0.45)]" /></div>
      <Prose eyebrow="Our craft" title="Textiles with a sense of place">
        <p>Era Wovens brings together silks, cottons, linens and organzas from India’s weaving traditions — the temple borders of Kanchipuram, the brocade jaals of Varanasi, the crisp checks of everyday handlooms.</p>
        <p>We describe every piece plainly: the fabric, how it was made, and the occasions it suits. When a saree is handwoven or hand block printed, we say so; when it is powerloom, we say that too.</p>
        <h2>How we price</h2>
        <p>Prices include GST. Shipping, offers and any cash-on-delivery fee are calculated by our servers at checkout and shown before you pay. We never show invented “was” prices or countdown timers.</p>
        <h2>Bulk orders</h2>
        <p>Weddings, teams and boutiques can order from 10 pieces, mixing styles and colours, with dedicated per-piece pricing on eligible styles.</p>
      </Prose>
    </>
  );
}
