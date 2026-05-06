import React, { useState, useEffect } from 'react'
import { api } from '../../utils/api'

export default function ExploreDashboard({ groupId }) {
  const [items, setItems] = useState([])
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)

  useEffect(() => {
    loadItems()
  }, [groupId, searchQuery, statusFilter, page])

  const loadItems = async () => {
    setLoading(true)
    try {
      const result = await api.searchItems(searchQuery, statusFilter, null, page, 12)
      setItems(Array.isArray(result) ? result : result.content || [])
    } catch (err) {
      console.error('Failed to load items:', err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="explore-dashboard">
      <div className="filters-row">
        <input
          type="text"
          placeholder="Search items..."
          className="search-input"
          value={searchQuery}
          onChange={(e) => {
            setSearchQuery(e.target.value)
            setPage(0)
          }}
        />
        <select
          className="filter-select"
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value)
            setPage(0)
          }}
        >
          <option value="">All statuses</option>
          <option value="AVAILABLE">Available</option>
          <option value="BORROWED">Borrowed</option>
        </select>
      </div>

      {loading ? (
        <div className="loading">Loading items...</div>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <p>No items found. Be the first to share something!</p>
        </div>
      ) : (
        <div className="items-grid">
          {items.map(item => (
            <div key={item.id} className="item-card">
              <div className="item-header">
                <h3 className="item-title">{item.title}</h3>
                <span className={`item-status status-${item.status?.toLowerCase()}`}>
                  {item.status || 'AVAILABLE'}
                </span>
              </div>
              <p className="item-description">{item.description || 'No description'}</p>
              <div className="item-footer">
                <small className="item-meta">
                  {item.owner?.fullName || 'Unknown owner'}
                </small>
                <button className="btn btn-sm btn-primary" disabled={item.status === 'BORROWED'}>
                  Request
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
