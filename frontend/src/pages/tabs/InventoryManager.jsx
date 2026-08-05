import React, { useState, useEffect } from 'react'
import { api } from '../../utils/api'
import { useApp } from '../../contexts/AppContext'

export default function InventoryManager({ userId }) {
  const { showToast, triggerRefresh, refreshTrigger } = useApp()
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [formData, setFormData] = useState({ title: '', description: '' })
  const [error, setError] = useState('')

  useEffect(() => {
    loadItems()
  }, [userId, refreshTrigger])

  const loadItems = async () => {
    setLoading(true)
    try {
      const allItems = await api.getItems()
      setItems(Array.isArray(allItems) ? allItems : [])
    } catch (err) {
      console.error('Failed to load items:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleCreateItem = async (e) => {
    e.preventDefault()
    if (!formData.title.trim()) {
      setError('Title is required')
      return
    }
    try {
      const newItem = await api.createItem(formData.title, formData.description)
      setItems([...items, newItem])
      setFormData({ title: '', description: '' })
      setShowCreateForm(false)
      setError('')
      showToast(`Item "${newItem.title}" added to inventory!`)
      triggerRefresh()
    } catch (err) {
      setError(err.message || 'Failed to create item')
    }
  }

  const handleArchiveItem = async (itemId) => {
    try {
      await api.archiveItem(itemId)
      showToast('Item archived successfully')
      loadItems()
      triggerRefresh()
    } catch (err) {
      showToast(err.message || 'Failed to archive item', 'error')
    }
  }

  return (
    <div className="inventory-manager">
      <div className="inventory-header">
        <button 
          className="btn btn-primary"
          onClick={() => setShowCreateForm(!showCreateForm)}
        >
          {showCreateForm ? '✕ Cancel' : '+ Add Item'}
        </button>
      </div>

      {showCreateForm && (
        <div className="create-form-card">
          <h3>Add New Item</h3>
          <form onSubmit={handleCreateItem}>
            <div className="form-group">
              <label>Item Title</label>
              <input
                type="text"
                placeholder="e.g., Cordless Drill"
                value={formData.title}
                onChange={(e) => setFormData({...formData, title: e.target.value})}
              />
            </div>
            <div className="form-group">
              <label>Description</label>
              <textarea
                placeholder="Tell others about this item..."
                value={formData.description}
                onChange={(e) => setFormData({...formData, description: e.target.value})}
                rows="3"
              />
            </div>
            {error && <div className="error-message">{error}</div>}
            <button type="submit" className="btn btn-primary">Create Item</button>
          </form>
        </div>
      )}

      {loading ? (
        <div className="loading">Loading inventory...</div>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <p>You haven't added any items yet. Create one to start sharing!</p>
        </div>
      ) : (
        <div className="items-table">
          <table>
            <thead>
              <tr>
                <th>Title</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map(item => (
                <tr key={item.id} className={item.archived ? 'archived' : ''}>
                  <td className="item-title-cell">
                    <strong>{item.title}</strong>
                    <br/>
                    <small>{item.description || 'No description'}</small>
                  </td>
                  <td>
                    <span className={`status-badge status-${item.status?.toLowerCase()}`}>
                      {item.status || 'AVAILABLE'}
                    </span>
                  </td>
                  <td><small>{new Date(item.createdAt).toLocaleDateString()}</small></td>
                  <td>
                    <button 
                      className="btn btn-sm btn-outline"
                      onClick={() => handleArchiveItem(item.id)}
                      disabled={item.archived}
                    >
                      {item.archived ? 'Archived' : 'Archive'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
