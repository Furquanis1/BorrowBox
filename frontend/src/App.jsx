import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { CommunityProvider } from './contexts/CommunityContext'
import { AppProvider } from './contexts/AppContext'
import PublicLayout from './components/layout/PublicLayout'
import AppShell from './components/layout/AppShell'
import LandingPage from './pages/LandingPage'
import SignInPage from './pages/SignInPage'
import SignUpPage from './pages/SignUpPage'
import ExplorePage from './pages/dashboard/ExplorePage'
import InventoryPage from './pages/dashboard/InventoryPage'
import RequestsPage from './pages/dashboard/RequestsPage'
import LoansPage from './pages/dashboard/LoansPage'
import MembersPage from './pages/dashboard/MembersPage'
import RulesPage from './pages/dashboard/RulesPage'
import Toast from './components/ui/Toast'

function ProtectedRoute({ children }) {
  const { user, initializingAuth } = useAuth()

  if (initializingAuth) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontFamily: 'var(--font-sans)',
        color: 'var(--teal-700)',
        fontWeight: 600
      }}>
        Loading Workspace...
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/signin" replace />
  }

  return children
}

function PublicRoute({ children }) {
  const { user, initializingAuth } = useAuth()

  if (initializingAuth) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontFamily: 'var(--font-sans)',
        color: 'var(--teal-700)',
        fontWeight: 600
      }}>
        Loading...
      </div>
    )
  }

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  return children
}

export default function App() {
  return (
    <AuthProvider>
      <CommunityProvider>
        <AppProvider>
          <Router>
            <Routes>
              <Route element={<PublicLayout />}>
                <Route path="/" element={<LandingPage />} />
                <Route
                  path="/signin"
                  element={
                    <PublicRoute>
                      <SignInPage />
                    </PublicRoute>
                  }
                />
                <Route
                  path="/signup"
                  element={
                    <PublicRoute>
                      <SignUpPage />
                    </PublicRoute>
                  }
                />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Route>

              <Route
                path="/dashboard"
                element={
                  <ProtectedRoute>
                    <AppShell />
                  </ProtectedRoute>
                }
              >
                <Route index element={<Navigate to="/dashboard/explore" replace />} />
                <Route path="explore" element={<ExplorePage />} />
                <Route path="inventory" element={<InventoryPage />} />
                <Route path="requests" element={<RequestsPage />} />
                <Route path="loans" element={<LoansPage />} />
                <Route path="members" element={<MembersPage />} />
                <Route path="rules" element={<RulesPage />} />
              </Route>
            </Routes>
            <Toast />
          </Router>
        </AppProvider>
      </CommunityProvider>
    </AuthProvider>
  )
}