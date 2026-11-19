import React, { useId } from 'react'

const INK = '#212529'
const SAGE = '#9CAF88'
const CLAY = '#D97757'
const MUTED = '#6c757d'

export const CATEGORY_FILTERS = ['All', 'Books', 'Electronics', 'Tools', 'Sports']

function searchableText(item = {}) {
  return [
    item.title,
    item.description,
    item.categoryName,
    item.category,
    item.name,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
}

export function getArtworkVariant(item = {}) {
  const text = searchableText(item)
  if (/sport|football|ball|soccer|basket|tennis|cricket/.test(text)) return 'ball'
  if (/tool|drill|hardware|screw|hammer|kit|wrench/.test(text)) return 'drill'
  if (/camera|photo|photograph|lens|camcorder/.test(text)) return 'camera'
  if (/book|textbook|notes?|novel|manual|study/.test(text)) return 'book'
  if (/electronic|laptop|cable|device|phone|charger|headphone|tablet|computer/.test(text)) return 'device'
  return 'share'
}

export function matchesSearchQuery(item = {}, query = '') {
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  return searchableText(item).includes(needle)
}

export function matchesCategory(item = {}, category = 'All') {
  if (!category || category === 'All') return true
  const text = searchableText(item)
  switch (category) {
    case 'Books':
      return /book|textbook|notes?|novel|manual|study/.test(text)
    case 'Electronics':
      return /electronic|camera|laptop|cable|device|phone|charger|headphone|tablet/.test(text)
    case 'Tools':
      return /tool|drill|hardware|screw|hammer|kit|wrench/.test(text)
    case 'Sports':
      return /sport|football|ball|soccer|basket|tennis|cricket/.test(text)
    default:
      return true
  }
}

function BallArt({ shades }) {
  return (
    <g>
      <circle cx="160" cy="86" r="52" fill={shades.paper} stroke={INK} strokeWidth="3.5" />
      <path
        d="M160 55 L184 72 L175 100 L145 100 L136 72 Z"
        fill={shades.sage}
        stroke={INK}
        strokeWidth="2.5"
        strokeLinejoin="round"
      />
      <path
        d="M160 55V34M184 72l23-9M175 100l18 18M145 100l-18 18M136 72l-23-9"
        stroke={INK}
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path d="M126 53c8-9 19-15 31-17" stroke={CLAY} strokeWidth="5" strokeLinecap="round" />
      <path
        d="M201 119c-8 6-17 10-27 12"
        stroke={INK}
        strokeOpacity="0.35"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <ellipse
        cx="138"
        cy="66"
        rx="11"
        ry="5"
        fill="#ffffff"
        opacity="0.9"
        transform="rotate(-30 138 66)"
      />
    </g>
  )
}

function DrillArt({ shades }) {
  return (
    <g strokeLinejoin="round">
      <rect
        x="90"
        y="61"
        width="114"
        height="46"
        rx="14"
        fill={shades.paper}
        stroke={INK}
        strokeWidth="3.5"
      />
      <path d="M118 61V49h62v12" fill={shades.sage} stroke={INK} strokeWidth="3" />
      <rect x="204" y="72" width="30" height="20" rx="6" fill={shades.sage} stroke={INK} strokeWidth="3" />
      <path d="M234 82h44" stroke={INK} strokeWidth="4" strokeLinecap="round" />
      <path
        d="M132 106l-12 32c-3 9 3 16 12 16h14c9 0 15-7 17-16l7-32"
        fill={shades.clay}
        stroke={INK}
        strokeWidth="3"
      />
      <rect x="124" y="148" width="54" height="14" rx="6" fill={INK} />
      <path d="M178 108c8 1 14 6 16 14" stroke={CLAY} strokeWidth="5" strokeLinecap="round" />
      <circle cx="117" cy="82" r="8" fill="#ffffff" stroke={INK} strokeWidth="2.5" />
      <circle cx="117" cy="82" r="3" fill={INK} />
      <path d="M147 70h42" stroke="#ffffff" strokeWidth="4" strokeLinecap="round" opacity="0.8" />
    </g>
  )
}

function LaptopArt({ shades }) {
  return (
    <g strokeLinejoin="round">
      <rect x="94" y="40" width="132" height="82" rx="10" fill={INK} />
      <rect x="102" y="48" width="116" height="64" rx="6" fill={shades.paper} />
      <rect x="112" y="58" width="96" height="44" rx="4" fill={shades.sage} opacity="0.35" />
      <path d="M138 122h44l24 20H114z" fill={shades.sage} stroke={INK} strokeWidth="3" />
      <path d="M130 130h60" stroke="#ffffff" strokeWidth="4" strokeLinecap="round" opacity="0.85" />
      <rect x="188" y="56" width="14" height="9" rx="3" fill={shades.clay} />
      <path d="M118 68h28" stroke="#ffffff" strokeWidth="4" strokeLinecap="round" opacity="0.85" />
      <path d="M118 90h18" stroke={INK} strokeOpacity="0.35" strokeWidth="3" strokeLinecap="round" />
    </g>
  )
}

function CameraArt({ shades }) {
  return (
    <g strokeLinejoin="round">
      <path
        d="M104 62h30l8-12h38l8 12h30c8 0 14 6 14 14v50c0 8-6 14-14 14H104c-8 0-14-6-14-14V76c0-8 6-14 14-14z"
        fill={shades.paper}
        stroke={INK}
        strokeWidth="3.5"
      />
      <path d="M104 62h30l8-12h38l8 12" fill={shades.sage} stroke={INK} strokeWidth="3" />
      <circle cx="164" cy="101" r="30" fill={INK} />
      <circle cx="164" cy="101" r="22" fill={shades.paper} stroke={shades.sage} strokeWidth="5" />
      <circle cx="164" cy="101" r="10" fill={shades.sage} stroke={INK} strokeWidth="2.5" />
      <circle cx="156" cy="93" r="4" fill="#ffffff" />
      <rect x="200" y="70" width="14" height="9" rx="3" fill={shades.clay} />
      <path d="M112 82h20" stroke="#ffffff" strokeWidth="4" strokeLinecap="round" opacity="0.85" />
    </g>
  )
}

function BookArt({ shades }) {
  return (
    <g strokeLinejoin="round">
      <path
        d="M94 54h106c8 0 14 6 14 14v62c0 8-6 14-14 14H94c-8 0-14-6-14-14V68c0-8 6-14 14-14z"
        fill={shades.paper}
        stroke={INK}
        strokeWidth="3.5"
      />
      <path d="M80 68c0-8 6-14 14-14v80c-8 0-14-6-14-14z" fill={shades.sage} stroke={INK} strokeWidth="3" />
      <path d="M206 68v62" stroke={INK} strokeWidth="3" />
      <path d="M112 76h66M112 92h54" stroke={INK} strokeWidth="5" strokeLinecap="round" />
      <path d="M112 110h48" stroke={MUTED} strokeWidth="3" strokeLinecap="round" />
      <path d="M182 54v34l9-7 9 7V54" fill={shades.clay} stroke={INK} strokeWidth="2.5" />
      <path d="M116 64h60" stroke="#ffffff" strokeWidth="3" strokeLinecap="round" opacity="0.8" />
    </g>
  )
}

function ShareBoxArt({ shades }) {
  return (
    <g strokeLinejoin="round">
      <path d="M104 74l56-28 56 28-56 28z" fill={shades.paper} stroke={INK} strokeWidth="3.5" />
      <path d="M104 74l56 28v42l-56-28z" fill={shades.sage} stroke={INK} strokeWidth="3.5" />
      <path d="M216 74l-56 28v42l56-28z" fill={shades.clay} stroke={INK} strokeWidth="3.5" />
      <circle cx="145" cy="73" r="5" fill="#ffffff" stroke={INK} strokeWidth="2" />
      <circle cx="175" cy="73" r="5" fill="#ffffff" stroke={INK} strokeWidth="2" />
      <path
        d="M145 73c8-10 20-10 30 0"
        fill="none"
        stroke={INK}
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M160 46V34M153 41l7-7 7 7"
        stroke={INK}
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M120 84l40 20" stroke="#ffffff" strokeWidth="3" strokeLinecap="round" opacity="0.75" />
    </g>
  )
}

const ARTWORK = {
  ball: BallArt,
  drill: DrillArt,
  camera: CameraArt,
  book: BookArt,
  device: LaptopArt,
  share: ShareBoxArt,
}

export default function CategoryArtwork({ title = '', category = '' }) {
  const rawId = useId().replace(/:/g, '')
  const gradientId = `tb-studio-${rawId}`
  const sageId = `tb-sage-${rawId}`
  const clayId = `tb-clay-${rawId}`
  const paperId = `tb-paper-${rawId}`
  const shadowId = `tb-shadow-${rawId}`
  const Art = ARTWORK[getArtworkVariant({ title, category })] || ShareBoxArt

  const shades = {
    sage: `url(#${sageId})`,
    clay: `url(#${clayId})`,
    paper: `url(#${paperId})`,
  }

  return (
    <svg
      className="tb-artwork"
      viewBox="0 0 320 176"
      preserveAspectRatio="xMidYMid slice"
      role="img"
      aria-label={`Studio illustration for ${title || 'shared item'}`}
      focusable="false"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#f3f2ee" />
          <stop offset="100%" stopColor="#e7e5de" />
        </linearGradient>
        <linearGradient id={sageId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#c5d2b7" />
          <stop offset="55%" stopColor={SAGE} />
          <stop offset="100%" stopColor="#7d946c" />
        </linearGradient>
        <linearGradient id={clayId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#eda587" />
          <stop offset="55%" stopColor={CLAY} />
          <stop offset="100%" stopColor="#b85535" />
        </linearGradient>
        <linearGradient id={paperId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="100%" stopColor="#f7f7f4" />
        </linearGradient>
        <filter id={shadowId} x="-30%" y="-30%" width="160%" height="180%">
          <feDropShadow dx="0" dy="8" stdDeviation="7" floodColor={INK} floodOpacity="0.14" />
        </filter>
      </defs>

      <rect width="320" height="176" fill={`url(#${gradientId})`} />
      <path d="M0 148C70 132 250 132 320 148V176H0Z" fill="#ffffff" opacity="0.22" />
      <ellipse cx="160" cy="149" rx="104" ry="11" fill={INK} opacity="0.08" />

      <g filter={`url(#${shadowId})`}>
        <Art shades={shades} />
      </g>

      <path
        d="M22 22h22M22 22v22M298 22h-22M298 22v22M22 154h22M22 154v-22M298 154h-22M298 154v-22"
        fill="none"
        stroke={INK}
        strokeOpacity="0.16"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  )
}
