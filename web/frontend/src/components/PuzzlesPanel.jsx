// Puzzle monitoring tab. Data: GET /api/dash/puzzles (same admin gate as /api/dash/overview).
import { useCallback, useEffect, useMemo, useState } from 'react';

const ago = (iso) => {
  if (!iso) return '-';
  const s = (Date.now() - new Date(iso).getTime()) / 1000;
  return s < 90 ? `${Math.round(s)}s ago` : s < 5400 ? `${Math.round(s / 60)}m ago` : s < 172800 ? `${Math.round(s / 3600)}h ago` : `${Math.round(s / 86400)}d ago`;
};
const pct = (v) => (v == null ? '-' : `${(v * 100).toFixed(1)}%`);
const dur = (ms) => (ms == null ? '-' : ms < 60000 ? `${(ms / 1000).toFixed(1)}s` : `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`);
const box = { border: '1px solid #8884', borderRadius: 8, padding: 10 };
const th = { textAlign: 'left', padding: '6px 8px', opacity: 0.6, fontWeight: 500, cursor: 'pointer', whiteSpace: 'nowrap', userSelect: 'none' };
const td = { padding: '6px 8px', borderTop: '1px solid #8883', verticalAlign: 'top' };
const h2 = { fontSize: 13, textTransform: 'uppercase', letterSpacing: '.05em', opacity: 0.6, margin: '24px 0 8px' };
const SEV = { critical: '#cf222e', warning: '#9a6700', info: '#0969da' };

const COLS = [
  ['puzzleType', 'Puzzle'], ['starts', 'Starts'], ['completes', 'Completes'], ['completionRate', 'Compl. rate'],
  ['fails', 'Fails'], ['abandons', 'Abandons'], ['hints', 'Hints'], ['avgHintsPerPlay', 'Hints/play'],
  ['errors', 'Errors'], ['errorRate', 'Err. rate'], ['medianDurationMs', 'Median time'], ['platforms', 'A / W'], ['lastSeen', 'Last seen'],
];
const sortVal = (p, k) => (k === 'platforms' ? p.platforms.android + p.platforms.web : k === 'lastSeen' ? (p.lastSeen ? Date.parse(p.lastSeen) : null) : p[k]);

function Trend({ daily }) {
  if (!daily.length) return <p style={{ opacity: 0.6 }}>No events in this range.</p>;
  const W = 640, H = 140, P = 24;
  const max = Math.max(1, ...daily.flatMap((d) => [d.starts, d.completes, d.errors]));
  const x = (i) => P + (daily.length === 1 ? (W - 2 * P) / 2 : (i * (W - 2 * P)) / (daily.length - 1));
  const y = (v) => H - P - (v / max) * (H - 2 * P);
  const line = (k) => daily.map((d, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(d[k]).toFixed(1)}`).join(' ');
  const series = [['starts', '#0969da'], ['completes', '#1a7f37'], ['errors', '#cf222e']];
  return (
    <div style={box}>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 'auto', maxHeight: 180 }} role="img" aria-label="Daily starts, completes and errors">
        <line x1={P} y1={H - P} x2={W - P} y2={H - P} stroke="#8886" />
        <text x={2} y={y(max) + 4} fontSize="10" fill="currentColor" opacity=".6">{max}</text>
        <text x={P} y={H - 6} fontSize="10" fill="currentColor" opacity=".6">{daily[0].date.slice(5)}</text>
        <text x={W - P} y={H - 6} fontSize="10" fill="currentColor" opacity=".6" textAnchor="end">{daily[daily.length - 1].date.slice(5)}</text>
        {series.map(([k, c]) => (
          <g key={k}>
            <path d={line(k)} fill="none" stroke={c} strokeWidth="2" />
            {daily.length === 1 && <circle cx={x(0)} cy={y(daily[0][k])} r="3" fill={c} />}
          </g>
        ))}
      </svg>
      <div style={{ display: 'flex', gap: 14, fontSize: 12 }}>
        {series.map(([k, c]) => <span key={k}><span style={{ display: 'inline-block', width: 10, height: 10, background: c, marginRight: 4 }} />{k}</span>)}
      </div>
    </div>
  );
}

export default function PuzzlesPanel({ token, endpoint = '/api/dash/puzzles' }) {
  const [days, setDays] = useState(7);
  const [platform, setPlatform] = useState('all');
  const [onlyNew, setOnlyNew] = useState(false);
  const [sort, setSort] = useState({ key: 'starts', dir: -1 });
  const [d, setD] = useState(null);
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setErr(''); setLoading(true);
    try {
      const r = await fetch(`${endpoint}?days=${days}&platform=${platform}`, {
        cache: 'no-store', headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      setD(await r.json());
    } catch (e) {
      setErr(e.message || 'failed to load');
    } finally { setLoading(false); }
  }, [days, platform, token, endpoint]);
  useEffect(() => { load(); }, [load]);

  const rows = useMemo(() => {
    if (!d) return [];
    const list = onlyNew ? d.byPuzzle.filter((p) => p.isNewWave) : d.byPuzzle;
    return [...list].sort((a, b) => {
      const av = sortVal(a, sort.key), bv = sortVal(b, sort.key);
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;
      return (typeof av === 'string' ? av.localeCompare(bv) : av - bv) * sort.dir;
    });
  }, [d, onlyNew, sort]);

  const noData = d && d.totals.starts + d.totals.completes + d.totals.errors + d.totals.fails + d.totals.abandons === 0;
  const cards = d ? [['Starts', d.totals.starts], ['Completes', d.totals.completes], ['Fails', d.totals.fails], ['Abandons', d.totals.abandons], ['Errors', d.totals.errors], ['Active installs', d.totals.activeInstalls]] : [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <h1 style={{ fontSize: 18, margin: 0 }}>Puzzles</h1>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <select value={days} onChange={(e) => setDays(Number(e.target.value))} style={{ padding: 6 }} aria-label="Range">
            <option value={1}>1 day</option><option value={7}>7 days</option><option value={30}>30 days</option>
          </select>
          <select value={platform} onChange={(e) => setPlatform(e.target.value)} style={{ padding: 6 }} aria-label="Platform">
            <option value="all">All platforms</option><option value="android">Android</option><option value="web">Web</option>
          </select>
          <label style={{ fontSize: 13 }}><input type="checkbox" checked={onlyNew} onChange={(e) => setOnlyNew(e.target.checked)} /> New games only</label>
          <button onClick={load} disabled={loading} style={{ padding: '6px 10px' }}>{loading ? 'Loading...' : 'Refresh'}</button>
        </div>
      </div>
      {err && <p style={{ color: '#cf222e' }}>Could not load: {err}</p>}
      {!d && !err && <p style={{ opacity: 0.6 }}>Loading...</p>}
      {d && (<>
        {d.alerts.length > 0 && (
          <div style={{ marginTop: 12, display: 'grid', gap: 6 }}>
            {d.alerts.filter((a) => a.severity !== 'info').map((a, i) => (
              <div key={i} style={{ border: `1px solid ${SEV[a.severity]}`, borderLeft: `4px solid ${SEV[a.severity]}`, borderRadius: 6, padding: '6px 10px' }}>
                <b style={{ color: SEV[a.severity], textTransform: 'uppercase', fontSize: 12 }}>{a.severity}</b> <b>{a.puzzleType}</b>: {a.message}
              </div>
            ))}
            {d.alerts.some((a) => a.severity === 'info') && (
              <details style={{ ...box, borderLeft: `4px solid ${SEV.info}` }}>
                <summary style={{ cursor: 'pointer' }}>{d.alerts.filter((a) => a.severity === 'info').length} info: no activity from new games</summary>
                <div style={{ opacity: 0.8, marginTop: 4 }}>{d.alerts.filter((a) => a.severity === 'info').map((a) => a.puzzleType).join(', ')}</div>
              </details>
            )}
          </div>
        )}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(140px,1fr))', gap: 8, marginTop: 12 }}>
          {cards.map(([k, v]) => <div key={k} style={box}><div style={{ opacity: 0.6, fontSize: 12 }}>{k}</div><div style={{ fontSize: 22, fontWeight: 600 }}>{v.toLocaleString()}</div></div>)}
        </div>
        {noData && <p style={{ opacity: 0.6 }}>No puzzle events in this range yet. New games appear below with zero counts until clients start reporting.</p>}

        <h2 style={h2}>Daily trend (UTC)</h2>
        <Trend daily={d.daily} />

        <h2 style={h2}>By puzzle ({rows.length})</h2>
        <div style={{ overflowX: 'auto' }}><table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr>{COLS.map(([k, label]) => (
            <th key={k} style={th} onClick={() => setSort((s) => ({ key: k, dir: s.key === k ? -s.dir : -1 }))}>
              {label}{sort.key === k ? (sort.dir < 0 ? ' ▾' : ' ▴') : ''}
            </th>))}</tr></thead>
          <tbody>
            {rows.length === 0 && <tr><td style={td} colSpan={COLS.length}><span style={{ opacity: 0.6 }}>No puzzles to show.</span></td></tr>}
            {rows.map((p) => (
              <tr key={p.puzzleType} style={p.isNewWave ? { background: '#0969da12' } : undefined}>
                <td style={td}><b>{p.puzzleType}</b>{p.isNewWave && <span style={{ marginLeft: 6, fontSize: 10, padding: '1px 5px', borderRadius: 8, background: '#0969da', color: '#fff' }}>new</span>}</td>
                <td style={td}>{p.starts}</td><td style={td}>{p.completes}</td>
                <td style={{ ...td, color: p.starts >= 20 && p.completionRate < 0.2 ? SEV.warning : undefined }}>{pct(p.completionRate)}</td>
                <td style={td}>{p.fails}</td><td style={td}>{p.abandons}</td><td style={td}>{p.hints}</td>
                <td style={td}>{p.avgHintsPerPlay == null ? '-' : p.avgHintsPerPlay.toFixed(2)}</td>
                <td style={td}>{p.errors}</td>
                <td style={{ ...td, color: p.starts >= 20 && p.errorRate > 0.05 ? SEV.critical : undefined }}>{pct(p.errorRate)}</td>
                <td style={td}>{dur(p.medianDurationMs)}</td>
                <td style={td}>{p.platforms.android} / {p.platforms.web}</td>
                <td style={td}>{ago(p.lastSeen)}</td>
              </tr>))}
          </tbody>
        </table></div>

        <h2 style={h2}>Top errors</h2>
        {d.topErrors.length === 0 ? <p style={{ opacity: 0.6 }}>No errors reported.</p> : (
          <div style={{ overflowX: 'auto' }}><table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Puzzle</th><th style={th}>Message</th><th style={th}>Count</th><th style={th}>Last</th></tr></thead>
            <tbody>{d.topErrors.map((e, i) => (
              <tr key={i}><td style={td}>{e.puzzleType}</td><td style={{ ...td, maxWidth: 520, wordBreak: 'break-word' }}>{e.message || '(no message)'}</td><td style={td}>{e.count}</td><td style={td}>{ago(e.lastSeen)}</td></tr>
            ))}</tbody>
          </table></div>
        )}
        <p style={{ opacity: 0.5, fontSize: 12, marginTop: 16 }}>Generated {ago(d.generatedAt)}. Rates are per start. A / W = Android / Web event counts.</p>
      </>)}
    </div>
  );
}
