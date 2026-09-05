import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Minimal async-data hook that runs `fn` (auto-firing on mount and whenever the
 * provided deps change) and exposes `{ data, loading, error, run, reload }`.
 *
 * - `run(...args)` can be used for manual/imperative reloads; the latest call wins.
 * - `reload` is an alias of `run`.
 */
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const requestKey = useRef(0)
  const fnRef = useRef(fn)
  fnRef.current = fn

  const run = useCallback(async (...args) => {
    const key = ++requestKey.current
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const data = await fnRef.current(...args)
      if (key === requestKey.current) {
        setState({ data, loading: false, error: null })
      }
      return data
    } catch (error) {
      if (key === requestKey.current) {
        setState((s) => ({ ...s, loading: false, error }))
      }
      throw error
    }
  }, [])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    run()
    return () => {
      requestKey.current += 1
    }
  }, [run, ...deps])

  return { ...state, run, reload: run }
}

export default useAsync