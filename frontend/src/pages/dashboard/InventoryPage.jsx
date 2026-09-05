import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function InventoryPage() {
  return (
    <div className="inventory-manager">
      <section aria-label="Inventory">
        <EmptyState
          icon="bi-box"
          title="Your inventory is empty"
          description="Asset management is coming soon. You'll be able to add and organize your items here."
        />
      </section>
    </div>
  )
}