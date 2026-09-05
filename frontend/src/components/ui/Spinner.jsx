import React from 'react'

export default function Spinner({ size = 'md', className = '' }) {
  const sizeClass = size === 'sm' ? ' spinner-sm' : ''
  return (
    <span className={`spinner${sizeClass} ${className}`.trim()} aria-hidden="true" role="presentation" />
  )
}