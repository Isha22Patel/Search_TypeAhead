import { useState, useCallback } from 'react'
import SearchBox    from './components/SearchBox.jsx'
import Trending     from './components/Trending.jsx'
import MetricsPanel from './components/MetricsPanel.jsx'

export default function App() {
  const [lastSearch,  setLastSearch]  = useState(null)
  const [suggestions, setSuggestions] = useState([])
  const [refreshKey,  setRefreshKey]  = useState(0)
  const [inputFill,   setInputFill]   = useState('')

  const handleSearch = useCallback((result) => {
    setLastSearch(result)
    setRefreshKey(k => k + 1)
  }, [])

  const handleTrendingSelect = useCallback((query) => {
    setInputFill(query)
  }, [])

  return (
    <div className="app">
      <header className="app-header">
        <h1>Search Typeahead System</h1>
        <div className="subtitle">
          <span className="badge accent">☕ Spring Boot 3</span>
          <span className="badge accent">🐘 PostgreSQL</span>
          <span className="badge">⚡ Consistent Hashing</span>
          <span className="badge">📦 Batch Writes</span>
          <span className="badge">🔥 Trending Engine</span>
        </div>
      </header>

      <main className="main-content">

        {/* ── Search ── */}
        <div className="search-section">
          <div className="card">
            <div className="card-title"><span className="dot" />Search</div>
            <SearchBox
              onSearch={handleSearch}
              onSuggestions={setSuggestions}
              prefillValue={inputFill}
            />
            {lastSearch && (
              <div style={{ marginTop: '1rem' }}>
                {lastSearch.success ? (
                  <div className="search-result-banner">
                    ✓ Searched for &ldquo;<strong>{lastSearch.query}</strong>&rdquo; — queued for batch write
                  </div>
                ) : (
                  <div className="search-error-banner">
                    ✗ Search failed — is the backend running?
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* ── Bottom: Trending + Metrics side by side ── */}
        <div className="bottom-grid">
          <Trending onSelect={handleTrendingSelect} refreshKey={refreshKey} />
          <MetricsPanel />
        </div>

      </main>
    </div>
  )
}
