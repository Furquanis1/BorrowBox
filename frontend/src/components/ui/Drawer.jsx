import React, { useEffect, useId, useRef } from 'react'
import IconButton from './IconButton'

export default function Drawer({
  open,
  onClose,
  title,
  className = '',
  backdropClassName = '',
  children,
}) {
  const titleId = useId()
  const closeRef = useRef(null)

  useEffect(() => {
    if (!open) return

    const previouslyFocused = document.activeElement
    const previousOverflow = document.body.style.overflow

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      if (previouslyFocused instanceof HTMLElement && document.contains(previouslyFocused)) {
        previouslyFocused.focus()
      }
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div className={`drawer-backdrop ${backdropClassName}`.trim()} onClick={onClose}>
      <div
        className={`drawer ${className}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="drawer-header">
          <h3 className="drawer-title" id={titleId}>
            {title}
          </h3>
          <IconButton ref={closeRef} label="Close" onClick={onClose}>
            <i className="bi bi-x-lg" aria-hidden="true" />
          </IconButton>
        </header>
        <div className="drawer-body">{children}</div>
      </div>
    </div>
  )
}