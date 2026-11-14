import type { SVGProps } from 'react';

const base = { width: 22, height: 22, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true } as const;

export const BagIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M5 8h14l-1 12H6L5 8Z" /><path d="M9 8V6a3 3 0 0 1 6 0v2" /></svg>);
export const HeartIcon = ({ filled, ...p }: SVGProps<SVGSVGElement> & { filled?: boolean }) => (<svg {...base} {...p}><path fill={filled ? 'currentColor' : 'none'} d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z" /></svg>);
export const SearchIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></svg>);
export const MenuIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M4 7h16M4 12h16M4 17h10" /></svg>);
export const CloseIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M6 6l12 12M18 6 6 18" /></svg>);
export const BoxIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M4 8 12 4l8 4v8l-8 4-8-4V8Z" /><path d="m4 8 8 4 8-4M12 12v8" /></svg>);
export const TruckIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M3 6h11v10H3zM14 10h4l3 3v3h-7" /><circle cx="7" cy="18" r="1.6" /><circle cx="17" cy="18" r="1.6" /></svg>);
export const ShieldIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M12 3 5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></svg>);
export const LoomIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M4 4v16M20 4v16M4 8h16M4 16h16M8 8v8M12 8v8M16 8v8" /></svg>);
export const FilterIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M4 6h16M7 12h10M10 18h4" /></svg>);
export const ChevronIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="m9 6 6 6-6 6" /></svg>);
export const EyeIcon = (p: SVGProps<SVGSVGElement>) => (<svg {...base} {...p}><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="2.8" /></svg>);
