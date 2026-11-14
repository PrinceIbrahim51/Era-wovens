import type { Motif } from '@/lib/types';

/**
 * Original, procedurally drawn textile artwork used as licensed-safe placeholder imagery.
 * Each product renders from its motif + variant colours, so no third-party photography is used.
 * Pure SVG (no hooks), safe in server and client components.
 */
export type ArtView = 'drape' | 'pallu' | 'border';

interface Props {
  motif: Motif;
  base: string;
  accent: string;
  view?: ArtView;
  uid: string;
  title?: string;
  className?: string;
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}
function mix(hex: string, target: string, amount: number): string {
  const a = hexToRgb(hex);
  const b = hexToRgb(target);
  const c = a.map((v, i) => Math.round(v + ((b[i] ?? 0) - v) * amount));
  return `#${c.map((v) => v.toString(16).padStart(2, '0')).join('')}`;
}
const lighten = (hex: string, amt: number) => mix(hex, '#ffffff', amt);
const darken = (hex: string, amt: number) => mix(hex, '#000000', amt);
function luminance(hex: string) {
  const [r, g, b] = hexToRgb(hex).map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** One repeat tile of the body motif, drawn in `fg`. */
function Tile({ motif, fg, s }: { motif: Motif; fg: string; s: number }) {
  switch (motif) {
    case 'butta':
      return (
        <g fill={fg}>
          {[[s * 0.25, s * 0.25], [s * 0.75, s * 0.75]].map(([x, y], i) => (
            <g key={i} transform={`translate(${x} ${y})`}>
              <circle r={s * 0.06} cy={-s * 0.08} />
              <circle r={s * 0.06} cy={s * 0.08} />
              <circle r={s * 0.06} cx={-s * 0.08} />
              <circle r={s * 0.06} cx={s * 0.08} />
              <circle r={s * 0.04} fill={lighten(fg, 0.5)} />
            </g>
          ))}
        </g>
      );
    case 'paisley':
      return (
        <g fill={fg} transform={`scale(${s / 36})`}>
          <path d="M18 5c8 0 12 10 6 18-4 5-12 6-12 0 0-4 4-5 6-3 2-4-6-7 0-15z" />
          <path d="M6 30c3 0 4 3 2 5-1 1-3 1-3 0 0-1 1-1 1-1 0-1-2-2 0-4z" opacity="0.8" transform="translate(18 -18)" />
          <circle cx="17" cy="15" r="1.6" fill={lighten(fg, 0.55)} />
        </g>
      );
    case 'checks':
      return (
        <g stroke={fg} fill="none">
          <path d={`M0 0H${s}M0 0V${s}`} strokeWidth={s * 0.08} />
          <path d={`M0 ${s / 2}H${s}M${s / 2} 0V${s}`} strokeWidth={s * 0.03} opacity="0.55" />
        </g>
      );
    case 'stripes':
      return (
        <g fill={fg}>
          <rect width={s * 0.14} height={s} />
          <rect x={s * 0.42} width={s * 0.05} height={s} opacity="0.6" />
          <rect x={s * 0.62} width={s * 0.05} height={s} opacity="0.6" />
        </g>
      );
    case 'floral':
      return (
        <g transform={`translate(${s / 2} ${s / 2})`} fill={fg}>
          {[0, 72, 144, 216, 288].map((a) => (
            <ellipse key={a} rx={s * 0.07} ry={s * 0.16} cy={-s * 0.14} transform={`rotate(${a})`} />
          ))}
          <circle r={s * 0.06} fill={lighten(fg, 0.55)} />
          <circle r={s * 0.03} cx={-s * 0.5} cy={-s * 0.5} />
          <circle r={s * 0.03} cx={s * 0.5} cy={s * 0.5} />
        </g>
      );
    case 'jaal':
      return (
        <g>
          <path d={`M${s / 2} 0L${s} ${s / 2}L${s / 2} ${s}L0 ${s / 2}Z`} fill="none" stroke={fg} strokeWidth={s * 0.05} />
          <circle cx={s / 2} cy={s / 2} r={s * 0.09} fill={fg} />
          <circle cx={0} cy={0} r={s * 0.05} fill={fg} />
          <circle cx={s} cy={s} r={s * 0.05} fill={fg} />
        </g>
      );
    case 'temple':
      return (
        <g fill={fg}>
          <path d={`M${s / 2} ${s * 0.3}l${s * 0.1} ${s * 0.2}-${s * 0.1} ${s * 0.2}-${s * 0.1}-${s * 0.2}z`} />
          <circle cx={0} cy={0} r={s * 0.04} />
          <circle cx={s} cy={s} r={s * 0.04} />
        </g>
      );
    case 'plain':
    default:
      return <path d={`M0 ${s}L${s} 0`} stroke={fg} strokeWidth={0.6} opacity="0.25" />;
  }
}

/** Repeating border ornament: temple spires for temple motifs, diamonds otherwise. */
function BorderRow({ motif, fg, w, h, y }: { motif: Motif; fg: string; w: number; h: number; y: number }) {
  const step = h * 1.1;
  const count = Math.ceil(w / step) + 1;
  return (
    <g fill={fg}>
      {Array.from({ length: count }, (_, i) => {
        const x = i * step;
        return motif === 'temple' ? (
          <path key={i} d={`M${x} ${y + h}L${x + step / 2} ${y}L${x + step} ${y + h}Z`} />
        ) : (
          <path key={i} d={`M${x + step / 2} ${y}L${x + step * 0.8} ${y + h / 2}L${x + step / 2} ${y + h}L${x + step * 0.2} ${y + h / 2}Z`} />
        );
      })}
    </g>
  );
}

export function ProductArt({ motif, base, accent, view = 'drape', uid, title, className }: Props) {
  const id = (k: string) => `${uid}-${view}-${k}`.replace(/[^a-zA-Z0-9-]/g, '');
  const darkBase = luminance(base) < 0.25;
  const motifColor = motif === 'plain' ? (darkBase ? lighten(base, 0.25) : darken(base, 0.25)) : accent;
  const zari = lighten(accent, darkBase ? 0.35 : 0.15);
  const zariDark = darken(accent, 0.3);
  const tile = view === 'pallu' ? 54 : view === 'border' ? 34 : 26;

  return (
    <svg viewBox="0 0 400 500" role="img" aria-label={title} className={className} preserveAspectRatio="xMidYMid slice">
      {title ? <title>{title}</title> : null}
      <defs>
        <pattern id={id('body')} width={tile} height={tile} patternUnits="userSpaceOnUse">
          <Tile motif={motif} fg={motifColor} s={tile} />
        </pattern>
        <pattern id={id('pallu')} width={tile * 0.8} height={tile * 0.8} patternUnits="userSpaceOnUse" patternTransform="rotate(0)">
          <Tile motif={motif === 'plain' ? 'butta' : motif} fg={accent} s={tile * 0.8} />
        </pattern>
        <pattern id={id('pleats')} width="44" height="500" patternUnits="userSpaceOnUse">
          <rect width="44" height="500" fill={`url(#${id('pleat-x')})`} />
        </pattern>
        <linearGradient id={id('pleat-x')} x1="0" x2="44" y1="0" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#000" stopOpacity="0" />
          <stop offset="0.45" stopColor="#000" stopOpacity="0.22" />
          <stop offset="0.6" stopColor="#fff" stopOpacity="0.1" />
          <stop offset="1" stopColor="#000" stopOpacity="0" />
        </linearGradient>
        <radialGradient id={id('bg')} cx="50%" cy="35%" r="75%">
          <stop offset="0" stopColor="#FBF8F2" />
          <stop offset="1" stopColor="#E9E0D2" />
        </radialGradient>
        <linearGradient id={id('sheen')} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" stopColor="#fff" stopOpacity="0.18" />
          <stop offset="0.35" stopColor="#fff" stopOpacity="0" />
          <stop offset="1" stopColor="#000" stopOpacity="0.12" />
        </linearGradient>
        <radialGradient id={id('shadow')} cx="50%" cy="50%" r="50%">
          <stop offset="0" stopColor="#000" stopOpacity="0.22" />
          <stop offset="1" stopColor="#000" stopOpacity="0" />
        </radialGradient>
        <clipPath id={id('hem')}>
          <path d="M92 74H308L320 440Q298 452 276 440T232 440T188 440T144 440T100 440Q88 446 80 440Z" />
        </clipPath>
      </defs>

      {view === 'drape' && (
        <g>
          <rect width="400" height="500" fill={`url(#${id('bg')})`} />
          <ellipse cx="200" cy="462" rx="150" ry="16" fill={`url(#${id('shadow')})`} />
          <g clipPath={`url(#${id('hem')})`}>
            <rect width="400" height="500" fill={base} />
            <rect width="400" height="500" fill={`url(#${id('body')})`} opacity="0.85" />
            {/* pallu panel */}
            <rect x="80" y="70" width="92" height="400" fill={darken(base, 0.08)} />
            <rect x="80" y="70" width="92" height="400" fill={`url(#${id('pallu')})`} />
            <rect x="168" y="70" width="5" height="400" fill={zari} />
            <rect x="176" y="70" width="2" height="400" fill={zari} opacity="0.8" />
            {/* side border */}
            <rect x="282" y="70" width="40" height="400" fill={accent} />
            <rect x="286" y="70" width="2" height="400" fill={zariDark} opacity="0.6" />
            <rect x="314" y="70" width="2" height="400" fill={zariDark} opacity="0.6" />
            <g transform="translate(290 70) rotate(90) translate(0 -22)">
              <BorderRow motif={motif} fg={zariDark} w={400} h={14} y={0} />
            </g>
            {/* hem border */}
            <rect x="60" y="414" width="300" height="40" fill={accent} />
            <rect x="60" y="418" width="300" height="2" fill={zariDark} opacity="0.6" />
            <BorderRow motif={motif} fg={zariDark} w={360} h={14} y={426} />
            <rect width="400" height="500" fill={`url(#${id('pleats')})`} />
            <rect width="400" height="500" fill={`url(#${id('sheen')})`} />
          </g>
          {/* tassels */}
          <g stroke={accent} strokeWidth="2" strokeLinecap="round">
            {[92, 104, 116, 128, 140, 152, 164].map((x) => (
              <line key={x} x1={x} y1={446} x2={x - 1} y2={462} />
            ))}
          </g>
          {/* rod */}
          <line x1="64" y1="72" x2="336" y2="72" stroke="#6E5530" strokeWidth="6" strokeLinecap="round" />
          <circle cx="60" cy="72" r="7" fill="#8C6A33" />
          <circle cx="340" cy="72" r="7" fill="#8C6A33" />
        </g>
      )}

      {view === 'pallu' && (
        <g>
          <rect width="400" height="500" fill={darken(base, 0.06)} />
          <rect width="400" height="500" fill={`url(#${id('pallu')})`} />
          <rect y="0" width="400" height="58" fill={accent} />
          <BorderRow motif={motif} fg={zariDark} w={400} h={18} y={20} />
          <rect y="58" width="400" height="4" fill={zari} />
          <rect y="404" width="400" height="4" fill={zari} />
          <rect y="408" width="400" height="62" fill={accent} />
          <BorderRow motif={motif} fg={zariDark} w={400} h={22} y={428} />
          <g stroke={accent} strokeWidth="3" strokeLinecap="round">
            {Array.from({ length: 26 }, (_, i) => 8 + i * 15.2).map((x) => (
              <line key={x} x1={x} y1={470} x2={x} y2={494} />
            ))}
          </g>
          <rect width="400" height="500" fill={`url(#${id('sheen')})`} />
        </g>
      )}

      {view === 'border' && (
        <g>
          <rect width="400" height="500" fill={base} />
          <rect width="400" height="300" fill={`url(#${id('body')})`} opacity="0.9" />
          <rect y="300" width="400" height="6" fill={zari} />
          <rect y="312" width="400" height="188" fill={accent} />
          <rect y="326" width="400" height="2" fill={zariDark} opacity="0.7" />
          <BorderRow motif={motif} fg={zariDark} w={400} h={46} y={350} />
          <rect y="414" width="400" height="2" fill={zariDark} opacity="0.7" />
          <BorderRow motif={motif === 'temple' ? 'jaal' : 'temple'} fg={lighten(accent, 0.25)} w={400} h={18} y={436} />
          <rect y="470" width="400" height="30" fill={darken(accent, 0.12)} />
          <rect width="400" height="500" fill={`url(#${id('sheen')})`} />
        </g>
      )}
    </svg>
  );
}
