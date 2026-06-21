import { useState, useEffect, useCallback } from 'react'

/**
 * MetricsPanel – polls GET /metrics and GET /batch/stats every 5 seconds.
 * Displays:
 *   - Cache hit rate (across all 3 hash ring nodes)
 *   - p50/p95/p99 latency for /suggest and /search
 *   - Batch write buffer size and write reduction %
 */
export default function MetricsPanel() {
  const [metrics,    setMetrics]    = useState(null)
  const [batchStats, setBatchStats] = useState(null)

  const fetchAll = useCallback(async () => {
    try {
      const [mRes, bRes] = await Promise.all([
        fetch('/metrics'),
        fetch('/batch/stats')
      ])
      const [mData, bData] = await Promise.all([mRes.json(), bRes.json()])
      setMetrics(mData)
      setBatchStats(bData)
    } catch { /* silent */ }
  }, [])

  useEffect(() => { fetchAll() }, [fetchAll])
  useEffect(() => {
    const t = setInterval(fetchAll, 5000)
    return () => clearInterval(t)
  }, [fetchAll])

  const fmt = (v, dec = 1) => v != null ? v.toFixed(dec) : '—'
  const fmtMs = (v) => v != null ? `${v.toFixed(1)}ms` : '—'

  const hitRateColor = metrics?.cacheHitRate >= 80 ? 'success'
    : metrics?.cacheHitRate >= 50 ? 'warning' : 'accent'

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>

      {/* ── Cache Metrics ── */}
      <div>
        <div className="card-title" style={{ marginBottom: '0.75rem' }}><span className="dot" />⚡ Cache Metrics</div>
        <div className="metrics-grid">
          <div className="metric-tile">
            <div className="metric-label">Hit Rate</div>
            <div className={`metric-value ${hitRateColor}`}>{fmt(metrics?.cacheHitRate)}%</div>
          </div>
          <div className="metric-tile">
            <div className="metric-label">Total Requests</div>
            <div className="metric-value accent">{(metrics?.totalRequests ?? 0).toLocaleString()}</div>
          </div>
          <div className="metric-tile">
            <div className="metric-label">Cache Hits</div>
            <div className="metric-value success">{(metrics?.cacheHits ?? 0).toLocaleString()}</div>
          </div>
          <div className="metric-tile">
            <div className="metric-label">Cache Misses</div>
            <div className="metric-value">{(metrics?.cacheMisses ?? 0).toLocaleString()}</div>
          </div>
        </div>
      </div>

      <div style={{ borderTop: '1px solid var(--border)' }} />

      {/* ── Latency ── */}
      <div>
        <div className="card-title" style={{ marginBottom: '0.75rem' }}><span className="dot" />⏱ Latency</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 1.5rem' }}>
          <div>
            <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '0.4rem' }}>/suggest</div>
            {['p50', 'p95', 'p99'].map(p => (
              <div className="latency-row" key={p}>
                <span className="latency-label">{p.toUpperCase()}</span>
                <span className="latency-val">{fmtMs(metrics?.suggest?.[`${p}Ms`])}</span>
              </div>
            ))}
            <div className="latency-row">
              <span className="latency-label">Samples</span>
              <span className="latency-val">{metrics?.suggest?.sampleCount ?? 0}</span>
            </div>
          </div>
          <div>
            <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '0.4rem' }}>/search</div>
            {['p50', 'p95', 'p99'].map(p => (
              <div className="latency-row" key={p}>
                <span className="latency-label">{p.toUpperCase()}</span>
                <span className="latency-val">{fmtMs(metrics?.search?.[`${p}Ms`])}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ borderTop: '1px solid var(--border)' }} />

      {/* ── Batch Write Stats ── */}
      <div>
        <div className="card-title" style={{ marginBottom: '0.75rem' }}><span className="dot" />🗄 Batch Write Buffer</div>
        <div className="metrics-grid">
          <div className="metric-tile">
            <div className="metric-label">Write Reduction</div>
            <div className={`metric-value ${(batchStats?.writeReductionPercent ?? 0) >= 80 ? 'success' : 'warning'}`}>
              {fmt(batchStats?.writeReductionPercent)}%
            </div>
          </div>
          <div className="metric-tile">
            <div className="metric-label">Buffer Size</div>
            <div className="metric-value accent">{batchStats?.bufferSize ?? 0}</div>
          </div>
          <div className="metric-tile">
            <div className="metric-label">Searches Recv.</div>
            <div className="metric-value">{(batchStats?.totalSearchesReceived ?? 0).toLocaleString()}</div>
          </div>
          <div className="metric-tile">
            <div className="metric-label">DB Writes</div>
            <div className="metric-value">{(batchStats?.totalDbWrites ?? 0).toLocaleString()}</div>
          </div>
        </div>
        <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'monospace', marginTop: '0.5rem' }}>
          Flush every 5s OR buffer ≥ 1000
        </div>
      </div>
    </div>
  )
}
