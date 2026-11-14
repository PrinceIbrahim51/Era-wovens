import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="container-x py-24 text-center">
      <p className="eyebrow">404</p>
      <h1 className="mt-3 font-display text-5xl text-plum">We couldn’t find that page</h1>
      <p className="mt-4 text-muted">It may have moved, or the link may be mistyped.</p>
      <div className="mt-8 flex justify-center gap-3"><Link href="/" className="btn-primary">Go home</Link><Link href="/collections/all" className="btn-outline">Browse sarees</Link></div>
    </div>
  );
}
