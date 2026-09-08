import React from 'react'
import EmptyState from '../../components/ui/EmptyState'

export default function ProfilePage() {
  return (
    <div className="profile-page">
      <section aria-label="Profile">
        <EmptyState
          icon="bi-person"
          title="Your profile"
          description="Profile customization is coming soon."
        />
      </section>
    </div>
  )
}