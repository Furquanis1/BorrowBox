import React from 'react'

export default function Pagination({
  currentPage = 0,
  totalPages = 1,
  onPageChange,
  totalElements = null,
  pageSize = 12,
}) {
  if (totalPages <= 1) return null

  const previousDisabled = currentPage <= 0
  const nextDisabled = currentPage >= totalPages - 1

  const handlePrev = () => {
    if (!previousDisabled) onPageChange(currentPage - 1)
  }

  const handleNext = () => {
    if (!nextDisabled) onPageChange(currentPage + 1)
  }

  const getPageNumbers = () => {
    const maxVisible = 5
    const pages = []

    if (totalPages <= maxVisible) {
      for (let index = 0; index < totalPages; index += 1) pages.push(index)
      return pages
    }

    pages.push(0)
    let start = Math.max(1, currentPage - 1)
    let end = Math.min(totalPages - 2, currentPage + 1)

    if (currentPage <= 2) end = 3
    if (currentPage >= totalPages - 3) start = totalPages - 4

    if (start > 1) pages.push('ellipsis-start')
    for (let index = start; index <= end; index += 1) pages.push(index)
    if (end < totalPages - 2) pages.push('ellipsis-end')
    pages.push(totalPages - 1)

    return pages
  }

  const startItem = currentPage * pageSize + 1
  const endItem = Math.min((currentPage + 1) * pageSize, totalElements ?? 0)

  return (
    <div className="pagination-container" role="navigation" aria-label="Pagination Navigation">
      <div className="pagination-summary">
        {totalElements !== null && totalElements > 0 ? (
          <span>
            Showing <strong>{startItem}</strong>–<strong>{endItem}</strong> of{' '}
            <strong>{totalElements}</strong> items
          </span>
        ) : (
          <span>
            Page <strong>{currentPage + 1}</strong> of <strong>{totalPages}</strong>
          </span>
        )}
      </div>

      <div className="pagination-controls">
        <button
          type="button"
          className="pagination-button"
          onClick={handlePrev}
          disabled={previousDisabled}
          aria-label="Previous page"
        >
          <i className="bi bi-chevron-left" aria-hidden="true" />
          Previous
        </button>

        <div className="pagination-pages">
          {getPageNumbers().map((pageItem, index) => {
            if (typeof pageItem === 'string') {
              return (
                <span key={`${pageItem}-${index}`} className="pagination-ellipsis" aria-hidden="true">
                  …
                </span>
              )
            }

            const isActive = pageItem === currentPage
            return (
              <button
                key={pageItem}
                type="button"
                className={`pagination-page${isActive ? ' active' : ''}`}
                onClick={() => onPageChange(pageItem)}
                aria-label={`Page ${pageItem + 1}`}
                aria-current={isActive ? 'page' : undefined}
              >
                {pageItem + 1}
              </button>
            )
          })}
        </div>

        <button
          type="button"
          className="pagination-button"
          onClick={handleNext}
          disabled={nextDisabled}
          aria-label="Next page"
        >
          Next
          <i className="bi bi-chevron-right" aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
