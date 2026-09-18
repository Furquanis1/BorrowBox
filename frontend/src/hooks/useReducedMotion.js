import { useState, useEffect } from 'react'

export function useReducedMotion() {
  const [reduced, setReduced] = useState(false)

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    setReduced(mediaQuery.matches)

    const handler = (event) => setReduced(event.matches)
    mediaQuery.addEventListener('change', handler)
    return () => mediaQuery.removeEventListener('change', handler)
  }, [])

  return reduced
}