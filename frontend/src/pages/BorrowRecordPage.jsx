import React from 'react'
import BorrowRecordSearch from '../components/BorrowRecordSearch'
import BorrowRecordCreate from '../components/BorrowRecordCreate'

export default function BorrowRecordPage() {
  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2>Borrow Records</h2>
          <p className="muted">Search existing records or create a new one.</p>
        </div>
      </div>

      <div className="borrow-record-page-grid">
        <BorrowRecordSearch />
        <BorrowRecordCreate />
      </div>
    </section>
  )
}