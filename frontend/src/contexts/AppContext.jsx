import React, { createContext, useContext, useState } from 'react'

const AppContext = createContext()

export function AppProvider({ children }) {
  const [toast, setToast] = useState(null)
  const [borrowModalItem, setBorrowModalItem] = useState(null)
  const [refreshTrigger, setRefreshTrigger] = useState(0)

  const showToast = (message, type = 'success') => {
    setToast({ message, type })
    setTimeout(() => setToast(null), 4000)
  }

  const hideToast = () => setToast(null)

  const openBorrowModal = (item) => {
    setBorrowModalItem(item)
  }

  const closeBorrowModal = () => {
    setBorrowModalItem(null)
  }

  const triggerRefresh = () => {
    setRefreshTrigger(prev => prev + 1)
  }

  return (
    <AppContext.Provider
      value={{
        toast,
        showToast,
        hideToast,
        borrowModalItem,
        openBorrowModal,
        closeBorrowModal,
        refreshTrigger,
        triggerRefresh
      }}
    >
      {children}
    </AppContext.Provider>
  )
}

export function useApp() {
  const context = useContext(AppContext)
  if (!context) {
    throw new Error('useApp must be used within an AppProvider')
  }
  return context
}
