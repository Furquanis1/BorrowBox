import React from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { AppProvider } from './contexts/AppContext'
import LandingPage from './pages/LandingPage'
import SignInPage from './pages/SignInPage'
import SignUpPage from './pages/SignUpPage'
import DashboardLayout from './pages/DashboardLayout'
import Header from './components/Header'
import Footer from './components/Footer'
import Toast from './components/Toast'

function ProtectedRoute({ children }) {
  const { user, initializingAuth } = useAuth()

  if (initializingAuth) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontFamily: 'DM Sans, sans-serif',
        color: 'var(--forest)',
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
        fontFamily: 'DM Sans, sans-serif',
        color: 'var(--forest)',
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
      <AppProvider>
        <Router>
          <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
            <Header />
            <main style={{ flex: '1 0 auto' }}>
              <Routes>
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
                <Route
                  path="/dashboard"
                  element={
                    <ProtectedRoute>
                      <DashboardLayout />
                    </ProtectedRoute>
                  }
                />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </main>
            <Footer />
            <Toast />
          </div>
        </Router>
      </AppProvider>
    </AuthProvider>
  )
}
