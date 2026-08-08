import React, { useState, useEffect } from 'react'
import { api } from '../../utils/api'
import { useApp } from '../../contexts/AppContext'
import Pagination from '../../components/Pagination'

export default function ActiveLoans({ userId }) {
  const { showToast, triggerRefresh, refreshTrigger } = useApp()
  const [loans, setLoans] = useState([])
  const [loading, setLoading] = useState(false)
  const [filterType, setFilterType] = useState('active')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const pageSize = 10

  useEffect(() => {
    loadLoans()
  }, [filterType, page, refreshTrigger])

  const loadLoans = async () => {
    setLoading(true)
    try {
      const result = await api.searchBorrowRecords(
        filterType === 'active' ? true : null,
        filterType === 'overdue' ? true : null,
        page,
        pageSize
      )
      if (Array.isArray(result)) {
        setLoans(result)
        setTotalPages(1)
        setTotalElements(result.length)
      } else {
        setLoans(result.content || [])
        // Support both nested result.page metadata (Spring Boot 3.3+) and flat result.totalPages/totalElements
        const pages = result.page?.totalPages ?? result.totalPages ?? 1
        const elements = result.page?.totalElements ?? result.totalElements ?? (result.content?.length || 0)
        setTotalPages(pages)
        setTotalElements(elements)
      }
    } catch (err) {
      console.error('Failed to load loans:', err)
      setLoans([])
      setTotalPages(1)
      setTotalElements(0)
    } finally {
      setLoading(false)
    }
  }

  const handleReturn = async (loanId) => {
    try {
      await api.returnBorrowRecord(loanId)
      showToast(`Loan #${loanId} marked as returned!`)
      triggerRefresh()
      loadLoans()
    } catch (err) {
      showToast(err.message || 'Failed to return item', 'error')
    }
  }

  const isOverdue = (dueAt) => {
    return new Date(dueAt) < new Date()
  }

  return (
    <div className="active-loans">
      <div className="filters-row">
        <button 
          className={`filter-btn ${filterType === 'active' ? 'active' : ''}`}
          onClick={() => {
            setFilterType('active')
            setPage(0)
          }}
        >
          Active Loans
        </button>
        <button 
          className={`filter-btn ${filterType === 'overdue' ? 'active' : ''}`}
          onClick={() => {
            setFilterType('overdue')
            setPage(0)
          }}
        >
          Overdue
        </button>
        <button 
          className={`filter-btn ${filterType === 'all' ? 'active' : ''}`}
          onClick={() => {
            setFilterType('all')
            setPage(0)
          }}
        >
          All Loans
        </button>
      </div>

      {loading ? (
        <div className="loading">Loading loans...</div>
      ) : loans.length === 0 ? (
        <div className="empty-state">
          <p>No loans in this category.</p>
        </div>
      ) : (
        <div className="loans-list">
          {loans.map(loan => (
            <div 
              key={loan.id} 
              className={`loan-card ${isOverdue(loan.dueAt) ? 'overdue' : ''} ${loan.returned ? 'returned' : ''}`}
            >
              <div className="loan-header">
                <h4 className="loan-title">Loan #{loan.id}</h4>
                <div className="loan-badges">
                  {loan.returned && (
                    <span className="badge badge-success">Returned</span>
                  )}
                  {!loan.returned && isOverdue(loan.dueAt) && (
                    <span className="badge badge-danger">OVERDUE</span>
                  )}
                </div>
              </div>

              <div className="loan-body">
                <div className="loan-row">
                  <span className="loan-label">Item:</span>
                  <span>Item #{loan.itemId}</span>
                </div>
                <div className="loan-row">
                  <span className="loan-label">Borrower:</span>
                  <span>User #{loan.borrowedByUserId}</span>
                </div>
                <div className="loan-row">
                  <span className="loan-label">Borrowed:</span>
                  <span>{new Date(loan.borrowedAt).toLocaleDateString()}</span>
                </div>
                <div className="loan-row">
                  <span className="loan-label">Due:</span>
                  <span className={isOverdue(loan.dueAt) && !loan.returned ? 'text-danger' : ''}>
                    {new Date(loan.dueAt).toLocaleDateString()}
                  </span>
                </div>
                {loan.returned && (
                  <div className="loan-row">
                    <span className="loan-label">Returned:</span>
                    <span>{new Date(loan.returnedAt).toLocaleDateString()}</span>
                  </div>
                )}
              </div>

              {!loan.returned && (
                <div className="loan-actions">
                  <button 
                    className="btn btn-primary btn-sm"
                    onClick={() => handleReturn(loan.id)}
                  >
                    ✓ Mark as Returned
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {!loading && totalElements > 0 && (
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={pageSize}
          onPageChange={(newPage) => setPage(newPage)}
        />
      )}
    </div>
  )
}
