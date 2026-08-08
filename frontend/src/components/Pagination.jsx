import React from 'react'

export default function Pagination({
  currentPage = 0,
  totalPages = 1,
  onPageChange,
  totalElements = null,
  pageSize = 12
}) {
  if (totalPages <= 1 && totalElements === null) {
    return null
  }

  const handlePrev = () => {
    if (currentPage > 0) {
      onPageChange(currentPage - 1)
    }
  }

  const handleNext = () => {
    if (currentPage < totalPages - 1) {
      onPageChange(currentPage + 1)
    }
  }

  // Generate page numbers array with smart ellipsis for large page counts
  const getPageNumbers = () => {
    const pages = []
    const maxVisible = 5

    if (totalPages <= maxVisible) {
      for (let i = 0; i < totalPages; i++) {
        pages.push(i)
      }
    } else {
      pages.push(0)

      let start = Math.max(1, currentPage - 1)
      let end = Math.min(totalPages - 2, currentPage + 1)

      if (currentPage <= 2) {
        end = 3
      }
      if (currentPage >= totalPages - 3) {
        start = totalPages - 4
      }

      if (start > 1) {
        pages.push('ellipsis-start')
      }

      for (let i = start; i <= end; i++) {
        pages.push(i)
      }

      if (end < totalPages - 2) {
        pages.push('ellipsis-end')
      }

      pages.push(totalPages - 1)
    }

    return pages
  }

  // Calculate range info (e.g. 1-12 of 34)
  const startItem = totalElements === 0 ? 0 : currentPage * pageSize + 1
  const endItem = Math.min((currentPage + 1) * pageSize, totalElements || 0)

  return (
    <div
      className="pagination-container"
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '16px',
        marginTop: '24px',
        paddingTop: '16px',
        borderTop: '1px solid #E5E7EB'
      }}
    >
      <div style={{ fontSize: '0.875rem', color: '#6B7280' }}>
        {totalElements !== null ? (
          totalElements > 0 ? (
            <span>
              Showing <strong>{startItem}</strong>–<strong>{endItem}</strong> of <strong>{totalElements}</strong> items
            </span>
          ) : (
            <span>No items</span>
          )
        ) : (
          <span>
            Page <strong>{currentPage + 1}</strong> of <strong>{totalPages || 1}</strong>
          </span>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
        <button
          type="button"
          className="btn btn-outline btn-sm"
          onClick={handlePrev}
          disabled={currentPage <= 0}
          style={{ cursor: currentPage <= 0 ? 'not-allowed' : 'pointer', opacity: currentPage <= 0 ? 0.5 : 1 }}
        >
          &laquo; Previous
        </button>

        {getPageNumbers().map((pageItem, index) => {
          if (typeof pageItem === 'string') {
            return (
              <span
                key={`${pageItem}-${index}`}
                style={{ padding: '0 4px', color: '#9CA3AF', fontSize: '0.875rem' }}
              >
                &hellip;
              </span>
            )
          }

          const isActive = pageItem === currentPage
          return (
            <button
              key={pageItem}
              type="button"
              className={`btn btn-sm ${isActive ? 'btn-primary' : 'btn-outline'}`}
              onClick={() => onPageChange(pageItem)}
              style={{
                minWidth: '34px',
                padding: '4px 8px',
                fontWeight: isActive ? '600' : '400'
              }}
            >
              {pageItem + 1}
            </button>
          )
        })}

        <button
          type="button"
          className="btn btn-outline btn-sm"
          onClick={handleNext}
          disabled={currentPage >= totalPages - 1}
          style={{
            cursor: currentPage >= totalPages - 1 ? 'not-allowed' : 'pointer',
            opacity: currentPage >= totalPages - 1 ? 0.5 : 1
          }}
        >
          Next &raquo;
        </button>
      </div>
    </div>
  )
}
