import React from 'react'
import { Outlet } from 'react-router-dom'
import Header from '../Header'
import Footer from '../Footer'
import SkipLink from './SkipLink'

export default function PublicLayout() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <SkipLink />
      <Header />
      <main id="main-content" className="public-content" tabIndex={-1}>
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}