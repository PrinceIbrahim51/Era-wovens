import type { Metadata } from 'next';
import { ProductArt } from '@/components/ProductArt';
import { Prose } from '@/components/Prose';

export const metadata: Metadata = { title: 'Our craft' };

export default function About() {
  return (
    <>
      <div className="container-x mt-10 overflow-hidden rounded-[26px]"><ProductArt uid="about" view="pallu" motif="jaal" base="#352433" accent="#C5A46D" className="aspect-[3/1] w-full" title="" /></div>
      <Prose eyebrow="Our craft" title="Textiles with a sense of place">
        <p>Aurelia brings together silks, cottons, linens and organzas from India’s weaving traditions — the temple borders of Kanchipuram, the brocade jaals of Varanasi, the crisp checks of everyday handlooms.</p>
        <p>We describe every piece plainly: the fabric, how it was made, and the occasions it suits. When a saree is handwoven or hand block printed, we say so; when it is powerloom, we say that too.</p>
        <h2>How we price</h2>
        <p>Prices include GST. Shipping, offers and any cash-on-delivery fee are calculated by our servers at checkout and shown before you pay. We never show invented “was” prices or countdown timers.</p>
        <h2>Bulk orders</h2>
        <p>Weddings, teams and boutiques can order from 10 pieces, mixing styles and colours, with dedicated per-piece pricing on eligible styles.</p>
      </Prose>
    </>
  );
}
