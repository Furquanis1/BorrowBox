import React from 'react'

export default function SkipLink() {
  const handleClick = (event) => {
    event.preventDefault()
    const target = document.getElementById('main-content')
    if (target) {
      target.focus()
      target.scrollIntoView()
    }
  }

  return (
    <a className="skip-link" href="#main-content" onClick={handleClick}>
      Skip to main content
    </a>
  )
}