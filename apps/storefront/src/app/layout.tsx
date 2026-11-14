import type { Metadata, Viewport } from 'next';
import { headers } from 'next/headers';
import { Cormorant_Garamond, Manrope } from 'next/font/google';
import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { Providers } from '@/components/Providers';
import { isPreviewMode } from '@/lib/mode';
import './globals.css';

const serif = Cormorant_Garamond({ subsets: ['latin'], weight: ['500', '600', '700'], variable: '--font-cormorant', display: 'swap' });
const sans = Manrope({ subsets: ['latin'], variable: '--font-manrope', display: 'swap' });

export const metadata: Metadata = {
  metadataBase: new URL(process.env.PUBLIC_APP_URL ?? 'http://localhost:3000'),
  title: { default: 'Era Wovens — Handwoven Sarees & Bulk Orders', template: '%s · Era Wovens' },
  description: 'Kanjivaram and Banarasi silks, handloom cottons and festive weaves. Bulk orders from 10 pieces.',
  openGraph: { type: 'website', siteName: 'Era Wovens' },
  robots: { index: false, follow: false }, // preview build
};

export const viewport: Viewport = { themeColor: '#352433', width: 'device-width', initialScale: 1 };

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Reading request headers opts every page into dynamic rendering so the per-request CSP nonce is applied.
  await headers();
  const preview = isPreviewMode();
  return (
    <html lang="en-IN" className={`${serif.variable} ${sans.variable}`}>
      <body className="min-h-dvh">
        <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[70] focus:rounded-lg focus:bg-plum focus:px-4 focus:py-2 focus:text-ivory">
          Skip to content
        </a>
        <Providers orderingOpen={!preview}>
          <Header preview={preview} />
          <main id="main" tabIndex={-1} className="focus:outline-none">{children}</main>
          <Footer />
        </Providers>
      </body>
    </html>
  );
}
