import React from 'react'

export default function Avatar({ name = '', size = 'md', className = '' }) {
  const initials = name
    .trim()
    .split(/\s+/)
    .map((part) => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <span className={`avatar avatar-${size} ${className}`.trim()} aria-hidden="true">
      {initials || '?'}
    </span>
  )
}