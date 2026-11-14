'use client';
import Link from 'next/link';

export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  return (
    <div className="container-x py-24 text-center" role="alert">
      <h1 className="font-display text-5xl text-plum">Something went wrong</h1>
      <p className="mt-4 text-muted">Please try again. If it keeps happening, the service may be briefly unavailable.</p>
      <div className="mt-8 flex justify-center gap-3"><button className="btn-primary" onClick={reset}>Try again</button><Link href="/" className="btn-outline">Go home</Link></div>
    </div>
  );
}
