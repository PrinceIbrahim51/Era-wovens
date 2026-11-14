import Link from 'next/link';
import { isPreviewMode } from '@/lib/mode';
import { Logo } from './Logo';

const cols: [string, [string, string][]][] = [
  ['Shop', [['/collections/kanjivaram-silks', 'Kanjivaram silks'], ['/collections/banarasi-weaves', 'Banarasi weaves'], ['/collections/handloom-cottons', 'Handloom cottons'], ['/collections/linen-organza', 'Linen & organza'], ['/bulk-orders', 'Bulk orders']]],
  ['Help', [['/track', 'Track an order'], ['/orders', 'My orders'], ['/faq', 'FAQ'], ['/policies/shipping', 'Shipping'], ['/policies/returns', 'Returns & refunds'], ['/contact', 'Contact us']]],
  ['Era Wovens', [['/about', 'Our craft'], ['/policies/privacy', 'Privacy policy'], ['/policies/terms', 'Terms of use']]],
];

export function Footer() {
  return (
    <footer className="mt-24 bg-plum text-ivory/85">
      <div className="container-x grid gap-12 py-16 md:grid-cols-[1.4fr_repeat(3,1fr)]">
        <div>
          <Logo tone="light" size={96} />
          <p className="mt-5 max-w-xs text-sm leading-relaxed text-ivory/75">
            Handwoven sarees and festive textiles, made for ceremonies large and small.
          </p>
          <p className="mt-4 max-w-xs rounded-lg border border-gold/40 px-3 py-2 text-xs text-ivory/80">
            {isPreviewMode()
              ? 'Catalogue preview: sample styles with original illustrative artwork. Ordering opens soon.'
              : 'Preview build: sample catalogue with original illustrative artwork. Online payments run in Razorpay test mode — no real money moves.'}
          </p>
        </div>
        {cols.map(([title, links]) => (
          <nav key={title} aria-label={title}>
            <p className="font-display text-xl text-gold">{title}</p>
            <ul className="mt-4 space-y-2.5 text-sm">
              {links.map(([href, label]) => (
                <li key={href}><Link href={href} className="hover:text-ivory hover:underline underline-offset-4">{label}</Link></li>
              ))}
            </ul>
          </nav>
        ))}
      </div>
      <div className="border-t border-ivory/10">
        <p className="container-x py-6 text-xs text-ivory/60">© {new Date().getFullYear()} Era Wovens (preview). Prices in INR, inclusive of GST.</p>
      </div>
    </footer>
  );
}
