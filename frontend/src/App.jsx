import React, { useState, useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Dashboard from './pages/Dashboard'
import Templates from './pages/Templates'
import Upload from './pages/Upload'
import Transactions from './pages/Transactions'
import Clusters from './pages/Clusters'
import Playground from './pages/Playground'
import Login from './pages/Login'
import Register from './pages/Register'
import Admin from './pages/Admin'
import './App.css'

function AppContent() {
  const { isAuthenticated, logout, user } = useAuth()
  const [theme, setTheme] = useState(() => {
    // Get theme from localStorage or default to 'light'
    return localStorage.getItem('theme') || 'light'
  })

  useEffect(() => {
    // Apply theme to document root
    document.documentElement.setAttribute('data-theme', theme)
    // Save theme preference to localStorage
    localStorage.setItem('theme', theme)
  }, [theme])

  const toggleTheme = () => {
    setTheme(prevTheme => prevTheme === 'light' ? 'dark' : 'light')
  }

  const handleLogout = () => {
    logout()
    window.location.href = '/login'
  }

  return (
    <Router>
      <div className="app">
        {isAuthenticated() && (
          <nav className="navbar">
            <div className="container">
              <div className="navbar-brand">
                <h1>SWIFT Template Extraction</h1>
              </div>
              <div className="navbar-menu">
                <Link to="/" className="navbar-item">Dashboard</Link>
                <Link to="/templates" className="navbar-item">Templates</Link>
                <Link to="/clusters" className="navbar-item">Clusters</Link>
                <Link to="/playground" className="navbar-item">Playground</Link>
                <Link to="/upload" className="navbar-item">Upload</Link>
                <Link to="/transactions" className="navbar-item">Transactions</Link>
                <Link to="/admin" className="navbar-item">Admin</Link>
                <button
                  className="theme-toggle"
                  onClick={toggleTheme}
                  title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
                >
                  {theme === 'light' ? '🌙' : '☀️'}
                </button>
                <button
                  className="button button-secondary"
                  onClick={handleLogout}
                  style={{ marginLeft: '1rem' }}
                >
                  Logout
                </button>
              </div>
            </div>
          </nav>
        )}

        <main className="main-content">
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <Dashboard />
                </ProtectedRoute>
              }
            />
            <Route
              path="/templates"
              element={
                <ProtectedRoute>
                  <Templates />
                </ProtectedRoute>
              }
            />
            <Route
              path="/clusters"
              element={
                <ProtectedRoute>
                  <Clusters />
                </ProtectedRoute>
              }
            />
            <Route
              path="/playground"
              element={
                <ProtectedRoute>
                  <Playground />
                </ProtectedRoute>
              }
            />
            <Route
              path="/upload"
              element={
                <ProtectedRoute>
                  <Upload />
                </ProtectedRoute>
              }
            />
            <Route
              path="/transactions"
              element={
                <ProtectedRoute>
                  <Transactions />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute>
                  <Admin />
                </ProtectedRoute>
              }
            />
          </Routes>
        </main>
      </div>
    </Router>
  )
}

function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  )
}

export default App
