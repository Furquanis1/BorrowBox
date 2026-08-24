import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import ExploreDashboard from './tabs/ExploreDashboard'
import InventoryManager from './tabs/InventoryManager'
import RequestInbox from './tabs/RequestInbox'
import ActiveLoans from './tabs/ActiveLoans'

export default function DashboardLayout() {
  const { user, groups, activeGroup, switchGroup, signOut } = useAuth()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState(() => {
    return localStorage.getItem('activeTab') || 'explore'
  })

  const handleTabChange = (tab) => {
    setActiveTab(tab)
    localStorage.setItem('activeTab', tab)
  }

  const handleSignOut = async () => {
    localStorage.removeItem('activeTab')
    await signOut()
    navigate('/')
  }

  if (!user) {
    return <div>Loading...</div>
  }

  const currentGroup = groups.find(g => g.id == activeGroup)

  return (
    <div className="dashboard">
      {/* SIDEBAR */}
      <aside className="dashboard-sidebar" aria-label="Sidebar Navigation">
        <div className="sidebar-header">
          <div className="sidebar-logo">📦 BorrowBox</div>
          <p className="sidebar-user">{user.fullName}</p>
        </div>

        <div className="sidebar-section">
          <h4 className="sidebar-section-title">Communities</h4>
          <div className="group-list" role="group" aria-label="Communities">
            {groups.map(group => (
              <button
                key={group.id}
                className={`group-item ${activeGroup === group.id ? 'active' : ''}`}
                onClick={() => switchGroup(group.id)}
                aria-pressed={activeGroup === group.id}
              >
                <span className="group-icon" aria-hidden="true">👥</span>
                <div className="group-info">
                  <div className="group-name">{group.name}</div>
                  <div className="group-meta">{group.users?.length || 0} members</div>
                </div>
              </button>
            ))}
          </div>
        </div>

        <nav className="sidebar-section" aria-label="Dashboard Navigation">
          <h4 className="sidebar-section-title">Navigation</h4>
          <div role="tablist" aria-orientation="vertical">
            <button
              id="tab-explore"
              role="tab"
              aria-selected={activeTab === 'explore'}
              aria-controls="panel-explore"
              className={`nav-item ${activeTab === 'explore' ? 'active' : ''}`}
              onClick={() => handleTabChange('explore')}
            >
              📊 Explore
            </button>
            <button
              id="tab-inventory"
              role="tab"
              aria-selected={activeTab === 'inventory'}
              aria-controls="panel-inventory"
              className={`nav-item ${activeTab === 'inventory' ? 'active' : ''}`}
              onClick={() => handleTabChange('inventory')}
            >
              📦 Inventory
            </button>
            <button
              id="tab-requests"
              role="tab"
              aria-selected={activeTab === 'requests'}
              aria-controls="panel-requests"
              className={`nav-item ${activeTab === 'requests' ? 'active' : ''}`}
              onClick={() => handleTabChange('requests')}
            >
              ✉️ Requests
            </button>
            <button
              id="tab-loans"
              role="tab"
              aria-selected={activeTab === 'loans'}
              aria-controls="panel-loans"
              className={`nav-item ${activeTab === 'loans' ? 'active' : ''}`}
              onClick={() => handleTabChange('loans')}
            >
              📋 Loans
            </button>
          </div>
        </nav>

        <div className="sidebar-footer">
          <button className="btn btn-ghost" style={{width:'100%'}} onClick={handleSignOut}>
            Sign Out
          </button>
        </div>
      </aside>

      {/* MAIN CONTENT */}
      <main className="dashboard-main">
        {/* TOP BAR */}
        <div className="dashboard-topbar">
          <div className="topbar-left">
            <h1 className="topbar-title">
              {activeTab === 'explore' && 'Explore Items'}
              {activeTab === 'inventory' && 'My Inventory'}
              {activeTab === 'requests' && 'Borrow Requests'}
              {activeTab === 'loans' && 'Active Loans'}
            </h1>
            {currentGroup && (
              <p className="topbar-subtitle">in <strong>{currentGroup.name}</strong></p>
            )}
          </div>
        </div>

        {/* TAB CONTENT */}
        <div
          className="dashboard-content"
          role="tabpanel"
          id={`panel-${activeTab}`}
          aria-labelledby={`tab-${activeTab}`}
        >
          {activeTab === 'explore' && <ExploreDashboard groupId={activeGroup} />}
          {activeTab === 'inventory' && <InventoryManager userId={user.id} />}
          {activeTab === 'requests' && <RequestInbox />}
          {activeTab === 'loans' && <ActiveLoans userId={user.id} />}
        </div>
      </main>
    </div>
  )
}
