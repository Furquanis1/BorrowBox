import React from 'react'
import Spinner from './Spinner'

export default function Button({
  as: Comp = 'button',
  variant = 'primary',
  size,
  block,
  loading,
  disabled,
  className = '',
  children,
  ...rest
}) {
  const classes = [
    'btn',
    `btn-${variant}`,
    size ? `btn-${size}` : '',
    block ? 'btn-block' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <Comp
      className={classes}
      disabled={disabled || loading || undefined}
      aria-busy={loading ? true : undefined}
      {...rest}
    >
      {loading ? <Spinner size="sm" /> : null}
      {children}
    </Comp>
  )
}