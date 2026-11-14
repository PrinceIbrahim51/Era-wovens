export function Prose({ eyebrow, title, children, draft = false }: { eyebrow?: string; title: string; children: React.ReactNode; draft?: boolean }) {
  return (
    <article className="container-x max-w-3xl py-14">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <h1 className="mt-3 font-display text-5xl text-plum">{title}</h1>
      {draft && (
        <p className="mt-6 rounded-xl border border-gold/50 bg-white px-5 py-4 text-sm text-gold-deep" role="note">
          Draft for the preview build. This text must be reviewed by a qualified adviser and replaced with the business’s real details before launch.
        </p>
      )}
      <div className="mt-8 space-y-5 leading-relaxed text-charcoal/90 [&_h2]:mt-10 [&_h2]:font-display [&_h2]:text-3xl [&_h2]:text-plum [&_li]:ml-5 [&_li]:list-disc [&_a]:font-semibold [&_a]:text-gold-deep [&_a]:underline">
        {children}
      </div>
    </article>
  );
}
