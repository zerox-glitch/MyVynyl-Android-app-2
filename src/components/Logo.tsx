import { useId } from 'react'

/**
 * Vynyl mark: a pressed record with a V-notch cut from the rim to the label — the V is negative space.
 * `spin` rotates the disc (the notch) for the launch animation; the tile stays put.
 */
export default function Logo({ size = 96, tile = true, className, discClass }: { size?: number; tile?: boolean; className?: string; discClass?: string }) {
  const id = useId().replace(/:/g, '')
  return (
    <svg viewBox="0 0 120 120" width={size} height={size} className={className} role="img" aria-label="Vynyl">
      {tile && <rect width="120" height="120" rx="27" fill="#d97706" />}
      <mask id={`v${id}`} maskUnits="userSpaceOnUse">
        <rect width="120" height="120" fill="#fff" />
        <path d="M41 10 L60 47 L79 10 Z" fill="#000" />
      </mask>
      <g className={discClass} style={{ transformOrigin: '60px 62px' }}>
        <g mask={`url(#v${id})`}>
          <circle cx="60" cy="62" r="45" fill="#14100d" />
          <circle cx="60" cy="62" r="36" fill="none" stroke="#f5e9d3" strokeOpacity=".16" strokeWidth="1.6" />
          <circle cx="60" cy="62" r="27" fill="none" stroke="#f5e9d3" strokeOpacity=".1" strokeWidth="1.6" />
        </g>
        <circle cx="60" cy="62" r="14" fill="#f5e9d3" />
        <circle cx="60" cy="62" r="2.6" fill="#14100d" />
      </g>
    </svg>
  )
}
