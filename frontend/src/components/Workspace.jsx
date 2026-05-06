import React, { useState } from 'react'
import ItemSearch from './ItemSearch'
import BorrowRecordSearch from './BorrowRecordSearch'
import BorrowRecordPage from '../pages/BorrowRecordPage'
import BorrowRequestCreate from './BorrowRequestCreate'
import BorrowRequestList from './BorrowRequestList'

const VIEWS = {
  items: 'items',
  records: 'records',
  requests: 'requests',
  requestList: 'requestList'
}

export default function Workspace({ onBackToLanding }) {
  const [view, setView] = useState(VIEWS.items)

  return (
    <div className="app-shell">
      <header className="app-header panel">
        <div>
          <h1>BorrowBox</h1>
          <p className="muted">Workspace</p>
        </div>

        <nav className="nav-tabs app-nav">
          <button type="button" onClick={() => setView(VIEWS.items)} disabled={view === VIEWS.items}>Items</button>
          <button type="button" onClick={() => setView(VIEWS.records)} disabled={view === VIEWS.records}>Borrow Records</button>
          <button type="button" onClick={() => setView(VIEWS.requests)} disabled={view === VIEWS.requests}>Create Request</button>
          <button type="button" onClick={() => setView(VIEWS.requestList)} disabled={view === VIEWS.requestList}>Request List</button>
          <button type="button" onClick={onBackToLanding} className="back-to-landing-btn">Back to Landing</button>
        </nav>
      </header>

      <main>
        {view === VIEWS.items && <ItemSearch />}
        {view === VIEWS.records && <BorrowRecordPage />}
        {view === VIEWS.requests && <BorrowRequestCreate />}
        {view === VIEWS.requestList && <BorrowRequestList />}
      </main>
    </div>
  )
}
