import Image from 'next/image';
import mark from '../../public/brand/era-wovens-mark.png';
import badge from '../../public/brand/era-wovens-badge-160.png';

/** Era Wovens brand lockup: the company medallion plus a serif wordmark. */
export function Logo({ className = '', tone = 'dark', size = 48 }: { className?: string; tone?: 'dark' | 'light'; size?: number }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <Image src={size > 56 ? badge : mark} alt="" width={size} height={size} priority className={size > 56 ? 'drop-shadow-[0_4px_18px_rgb(197_164_109/0.5)]' : 'rounded-full shadow-[0_2px_8px_rgb(122_91_35/0.25)]'} />
      <span className={`font-display leading-none ${tone === 'light' ? 'text-ivory' : 'text-plum'}`}>
        <span className="block text-[1.7rem] font-semibold italic tracking-[0.02em]">Era Wovens</span>
      </span>
    </span>
  );
}
