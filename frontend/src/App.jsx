import React, { useState } from 'react'
import LandingPage from './pages/LandingPage'
import Workspace from './components/Workspace'

export default function App() {
  const [mode, setMode] = useState('landing')

  return (
    <>
      {mode === 'landing' && (
        <LandingPage onEnter={() => setMode('workspace')} />
      )}
      {mode === 'workspace' && (
        <Workspace onBackToLanding={() => setMode('landing')} />
      )}
    </>
  )
}
