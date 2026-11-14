export function Logo({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
        <path d="M16 2 29 16 16 30 3 16Z" fill="none" stroke="#C5A46D" strokeWidth="1.5" />
        <path d="M16 8 24 16 16 24 8 16Z" fill="#352433" />
        <circle cx="16" cy="16" r="2.4" fill="#C5A46D" />
      </svg>
      <span className="font-display text-[1.65rem] font-semibold leading-none tracking-[0.18em] text-plum">AURELIA</span>
    </span>
  );
}
