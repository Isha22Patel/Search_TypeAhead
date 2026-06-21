import { useState, useRef, useCallback, useEffect } from 'react'

/**
 * SearchBox – controlled input with:
 *   - 300ms debounce on /suggest calls
 *   - Keyboard navigation: ↑ ↓ Enter Escape
 *   - Loading spinner during fetch
 *   - Controlled submit via Enter or clicking a suggestion
 *
 * Props:
 *   onSearch(query) — called when user submits a search
 *   onSuggestions(suggestions) — lifts suggestions up to parent
 */
export default function SearchBox({ onSearch, onSuggestions }) {
  const [value,       setValue]       = useState('')
  const [suggestions, setSuggestions] = useState([])
  const [isLoading,   setIsLoading]   = useState(false)
  const [activeIdx,   setActiveIdx]   = useState(-1)
  const [open,        setOpen]        = useState(false)
  const [error,       setError]       = useState(null)

  const debounceTimer = useRef(null)
  const inputRef      = useRef(null)

  // ── Fetch suggestions with 300ms debounce ────────────────────────────────
  const fetchSuggestions = useCallback(async (prefix) => {
    if (!prefix.trim()) {
      setSuggestions([])
      onSuggestions([])
      setOpen(false)
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const res  = await fetch(`/suggest?q=${encodeURIComponent(prefix.trim())}`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      const s    = data.suggestions || []
      setSuggestions(s)
      onSuggestions(s)
      setOpen(s.length > 0)
      setActiveIdx(-1)
    } catch (err) {
      setError('Could not fetch suggestions.')
      setOpen(false)
    } finally {
      setIsLoading(false)
    }
  }, [onSuggestions])

  const handleChange = (e) => {
    const v = e.target.value
    setValue(v)
    clearTimeout(debounceTimer.current)
    // Debounce: wait 300ms after the user stops typing before fetching
    debounceTimer.current = setTimeout(() => fetchSuggestions(v), 300)
  }

  // ── Submit search (POST /search) ─────────────────────────────────────────
  const submitSearch = useCallback(async (query) => {
    if (!query.trim()) return
    setValue(query)
    setSuggestions([])
    setOpen(false)
    setActiveIdx(-1)
    try {
      const res = await fetch('/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: query.trim() })
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      onSearch({ query: data.query, success: true })
    } catch {
      onSearch({ query, success: false })
    }
  }, [onSearch])

  // ── Keyboard navigation ──────────────────────────────────────────────────
  const handleKeyDown = (e) => {
    if (!open) {
      if (e.key === 'Enter') submitSearch(value)
      return
    }
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault()
        setActiveIdx(i => Math.min(i + 1, suggestions.length - 1))
        break
      case 'ArrowUp':
        e.preventDefault()
        setActiveIdx(i => Math.max(i - 1, -1))
        break
      case 'Enter':
        e.preventDefault()
        if (activeIdx >= 0) {
          submitSearch(suggestions[activeIdx].query)
        } else {
          submitSearch(value)
        }
        break
      case 'Escape':
        setOpen(false)
        setActiveIdx(-1)
        break
      default: break
    }
  }

  const handleSuggestionClick = (suggestion) => {
    submitSearch(suggestion.query)
  }

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e) => {
      if (!e.target.closest('.search-wrapper')) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // Highlight the matching prefix in bold
  const renderHighlighted = (query, prefix) => {
    if (!prefix || !query.toLowerCase().startsWith(prefix.toLowerCase())) {
      return <span className="suggestion-text">{query}</span>
    }
    return (
      <span className="suggestion-text">
        <span className="match">{query.slice(0, prefix.length)}</span>
        {query.slice(prefix.length)}
      </span>
    )
  }

  return (
    <div className="search-wrapper">
      <div className="search-input-container">
        <span className="search-icon">⌕</span>
        <input
          ref={inputRef}
          id="search-input"
          type="text"
          className="search-input"
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
          placeholder="Start typing to search…"
          autoComplete="off"
          spellCheck="false"
          aria-label="Search"
          aria-autocomplete="list"
          aria-expanded={open}
        />
        {isLoading && <div className="search-loading" aria-label="Loading" />}
      </div>

      {/* Error state */}
      {error && !open && (
        <div style={{ marginTop: '0.5rem', color: 'var(--danger)', fontSize: '0.82rem' }}>
          {error}
        </div>
      )}

      {/* Keyboard hints */}
      <div className="keyboard-hint">
        <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
        <span><kbd>Enter</kbd> search</span>
        <span><kbd>Esc</kbd> close</span>
      </div>

      {/* Suggestions dropdown */}
      {open && (
        <div className="suggestions-dropdown" role="listbox">
          {suggestions.map((s, idx) => (
            <div
              key={s.query}
              className={`suggestion-item ${idx === activeIdx ? 'active' : ''}`}
              role="option"
              aria-selected={idx === activeIdx}
              onMouseDown={() => handleSuggestionClick(s)}
              onMouseEnter={() => setActiveIdx(idx)}
            >
              {renderHighlighted(s.query, value.trim())}
              <div className="suggestion-meta">
                <span className="suggestion-count">{s.count.toLocaleString()}</span>
                {s.score !== s.count && (
                  <span style={{ color: 'var(--accent-light)', fontSize: '0.7rem' }}>
                    ↑{Math.round(s.score).toLocaleString()}
                  </span>
                )}
              </div>
            </div>
          ))}
          {suggestions.length === 0 && (
            <div className="no-suggestions">No suggestions found</div>
          )}
        </div>
      )}
    </div>
  )
}
