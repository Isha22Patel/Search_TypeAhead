import { useState, useEffect, useCallback } from 'react'

/**
 * TrendingSection – polls GET /trending every 30 seconds.
 * Displays top 10 queries with recency score and recent-count badge.
 *
 * Props:
 *   onSelect(query) — called when a trending item is clicked (fills search box)
 *   refreshKey     — increment to force a refresh after each search
 */
export default function TrendingSection({ onSelect, refreshKey }) {
  const [trending,  setTrending]  = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [lastFetch, setLastFetch] = useState(null)

  const fetchTrending = useCallback(async () => {
    setIsLoading(true)
    try {
      const res  = await fetch('/trending')
      const data = await res.json()
      setTrending(data.trending || [])
      setLastFetch(new Date())
    } catch {
      // Silently fail — trending is non-critical
    } finally {
      setIsLoading(false)
    }
  }, [])

  // Initial fetch + after every search
  useEffect(() => { fetchTrending() }, [fetchTrending, refreshKey])

  // Poll every 30 seconds for live updates
  useEffect(() => {
    const interval = setInterval(fetchTrending, 30_000)
    return () => clearInterval(interval)
  }, [fetchTrending])

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div className="card-title">
        <span className="dot" />
        🔥 Trending Searches
        {isLoading && (
          <span style={{ marginLeft: 'auto', fontSize: '0.65rem', color: 'var(--text-muted)' }}>
            updating…
          </span>
        )}
        {lastFetch && !isLoading && (
          <span style={{ marginLeft: 'auto', fontSize: '0.65rem', color: 'var(--text-muted)' }}>
            {lastFetch.toLocaleTimeString()}
          </span>
        )}
      </div>

      {trending.length === 0 ? (
        <div className="trending-empty">
          <div style={{ fontSize: '1.8rem', marginBottom: '0.5rem' }}>🔍</div>
          <div>No trending searches yet.</div>
          <div style={{ fontSize: '0.8rem', marginTop: '0.3rem' }}>
            Submit some searches to see trends!
          </div>
        </div>
      ) : (
        <div className="trending-list" style={{ flex: 1 }}>
          {trending.map((item, idx) => (
            <div
              key={item.query}
              className="trending-item"
              onClick={() => onSelect(item.query)}
              title={`Score: ${item.score.toFixed(1)} | Historical: ${item.historicalCount} | Recent (1h): ${item.recentCount}`}
            >
              <span className={`trending-rank ${idx < 3 ? 'top3' : ''}`}>
                {idx < 3 ? ['①','②','③'][idx] : `${idx + 1}`}
              </span>
              <span className="trending-query">{item.query}</span>
              {item.recentCount > 0 && (
                <span className="trending-recent-badge">
                  +{item.recentCount} recent
                </span>
              )}
              <span className="trending-score">
                {Math.round(item.score).toLocaleString()}
              </span>
            </div>
          ))}
        </div>
      )}

      {/* Formula explanation for viva */}
      <div style={{
        marginTop: '1rem',
        padding: '0.6rem 0.8rem',
        background: 'rgba(255,255,255,0.02)',
        borderRadius: '6px',
        fontSize: '0.7rem',
        color: 'var(--text-muted)',
        fontFamily: 'monospace',
        lineHeight: 1.6
      }}>
        score = 0.3×historical + 0.7×recent(1h)
      </div>
    </div>
  )
}
