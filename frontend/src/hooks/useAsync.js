import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Minimal async-data hook that runs `fn` (auto-firing on mount and whenever the
 * provided deps change) and exposes `{ data, loading, error, run, reload, retry }`.
 *
 * - `run(...args)` / `reload(...args)` can be used for manual/imperative reloads;
 *   the latest call wins. These REJECT on error so callers can catch and show toasts.
 * - `retry(...args)` is for fire-and-forget retry buttons; it does NOT reject,
 *   avoiding unhandled promise rejections when used directly as onClick handlers.
 * - The initial automatic execution does NOT rethrow.
 */
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const requestKey = useRef(0)
  const fnRef = useRef(fn)
  fnRef.current = fn

  const execute = useCallback(async (...args) => {
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
      // Do not rethrow for automatic initial-load
    }
  }, [])

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

  // Fire-and-forget retry for buttons; never rejects
  const retry = useCallback(async (...args) => {
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
      // Swallow error - state is updated, UI will re-render with error
    }
  }, [])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    execute()
    return () => {
      requestKey.current += 1
    }
  }, [execute, ...deps])

  return { ...state, run, reload: run, retry }
}

export default useAsync