import type { Product } from '@/lib/types';

/** Factual badges only — no invented scarcity or urgency. */
export function Badges({ product, className = '' }: { product: Product; className?: string }) {
  const items: { label: string; tone: string }[] = [];
  if (product.inStock === false) items.push({ label: 'Sold out', tone: 'bg-charcoal text-ivory' });
  if (product.isNew) items.push({ label: 'New', tone: 'bg-plum text-ivory' });
  if (/hand/i.test(product.craft)) items.push({ label: product.craft.includes('Block') ? 'Block printed' : 'Handwoven', tone: 'bg-ivory/95 text-plum ring-1 ring-line' });
  if (product.bulkEligible) items.push({ label: 'Bulk', tone: 'bg-gold text-charcoal' });
  if (!items.length) return null;
  return (
    <ul className={`flex flex-wrap gap-1.5 ${className}`} aria-label="Product highlights">
      {items.slice(0, 3).map((b) => (
        <li key={b.label} className={`rounded-full px-2.5 py-1 text-[0.65rem] font-bold uppercase tracking-wider ${b.tone}`}>{b.label}</li>
      ))}
    </ul>
  );
}
