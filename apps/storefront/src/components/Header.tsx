import Link from 'next/link';
import { getCollections } from '@/lib/server-api';
import type { Collection } from '@/lib/types';
import { HeaderClient } from './HeaderClient';
import { Logo } from './Logo';

export async function Header() {
  let collections: Collection[] = [];
  try {
    collections = await getCollections();
  } catch {
    // Navigation still renders with static links if the catalogue is unreachable.
  }
  return (
    <header className="sticky top-0 z-40 border-b border-line/70 bg-ivory/90 backdrop-blur-md">
      <div className="bg-plum text-ivory">
        <p className="container-x py-2 text-center text-[0.72rem] tracking-[0.14em] uppercase">
          <span className="hidden sm:inline">Free shipping over ₹2,999 · Bulk orders from 10 pieces · </span><span className="text-gold">Preview store — payments in test mode</span>
        </p>
      </div>
      <div className="container-x flex h-[4.5rem] items-center gap-4">
        <Link href="/" className="shrink-0" aria-label="Aurelia home"><Logo /></Link>
        <HeaderClient collections={collections} />
      </div>
    </header>
  );
}
