import React from 'react'

const IconButton = React.forwardRef(function IconButton(
  { label, children, className = '', ...rest },
  ref
) {
  return (
    <button type="button" ref={ref} className={`icon-button ${className}`.trim()} aria-label={label} {...rest}>
      {children}
    </button>
  )
})

export default IconButton