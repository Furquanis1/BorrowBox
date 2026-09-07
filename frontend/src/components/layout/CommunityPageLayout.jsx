import React from 'react'
import { Outlet } from 'react-router-dom'
import CommunityHeader from './CommunityHeader'
import CommunityTabs from './CommunityTabs'

export default function CommunityPageLayout() {
  return (
    <div className="community-page">
      <CommunityHeader />
      <CommunityTabs />
      <Outlet />
    </div>
  )
}