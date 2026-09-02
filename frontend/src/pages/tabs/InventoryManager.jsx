import React from 'react'

export default function InventoryManager({ userId }) {
  return (
    <div className="inventory-manager">
      <div className="empty-state">
        <p>Your inventory is empty. Asset management will be available in a future update.</p>
      </div>
    </div>
  )
}
