import React, { useState, useEffect } from 'react'
import { api } from '../../utils/api'
import { useAuth } from '../../contexts/AuthContext'
import { useApp } from '../../contexts/AppContext'
import ItemList from '../../components/ItemList'
import Pagination from '../../components/Pagination'

export default function ExploreDashboard({ groupId }) {
  const { user } = useAuth()
  const { refreshTrigger } = useApp()
  const [items, setItems] = useState([])
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const pageSize = 12

  useEffect(() => {
    loadItems()
  }, [groupId, searchQuery, statusFilter, page, refreshTrigger])

  const loadItems = async () => {
    setLoading(true)
    try {
      const result = await api.searchItems(searchQuery, statusFilter, null, page, pageSize)
      if (Array.isArray(result)) {
        setItems(result)
        setTotalPages(1)
        setTotalElements(result.length)
      } else {
        setItems(result.content || [])
        // Support both nested result.page metadata (Spring Boot 3.3+) and flat result.totalPages/totalElements
        const pages = result.page?.totalPages ?? result.totalPages ?? 1
        const elements = result.page?.totalElements ?? result.totalElements ?? (result.content?.length || 0)
        setTotalPages(pages)
        setTotalElements(elements)
      }
    } catch (err) {
      console.error('Failed to load items:', err)
      setItems([])
      setTotalPages(1)
      setTotalElements(0)
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

      <ItemList
        items={items}
        loading={loading}
        currentUserId={user?.id}
        emptyMessage="No items found. Be the first to share something in your community!"
      />

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
