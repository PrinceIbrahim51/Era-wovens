import type { Metadata } from 'next';
import { Prose } from '@/components/Prose';

export const metadata: Metadata = { title: 'FAQ' };

const QA: [string, string][] = [
  ['How do bulk orders work?', 'Add any bulk-eligible styles to your bulk bag. Once you have at least 10 pieces in total (mixing styles and colours is fine), you can check out. Bulk and regular bags are kept separate.'],
  ['Is cash on delivery available?', 'COD is offered on retail orders up to ₹25,000 for eligible PIN codes, with a ₹49 COD fee. Bulk orders are paid online. Eligibility is shown at checkout.'],
  ['Are online payments real in this store?', 'Not yet. This preview uses Razorpay Test Mode, so no real money moves. Use Razorpay’s published test UPI IDs or cards.'],
  ['Do prices include tax?', 'Yes. All prices include GST, and the GST amount is shown in your order summary.'],
  ['What does the artwork show?', 'Product images in this preview are original illustrations generated from each saree’s motif and colourway. Photography will replace them before launch.'],
  ['Can I change or cancel my order?', 'Contact us as soon as possible with your order number. Orders that have not yet shipped can usually be cancelled.'],
];

export default function Faq() {
  return (
    <Prose eyebrow="Help" title="Frequently asked questions">
      <div className="divide-y divide-line border-y border-line">
        {QA.map(([q, a]) => (
          <details key={q} className="group py-5">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold">{q}<span aria-hidden className="text-xl text-gold-deep transition group-open:rotate-45">+</span></summary>
            <p className="mt-3 text-muted">{a}</p>
          </details>
        ))}
      </div>
    </Prose>
  );
}
