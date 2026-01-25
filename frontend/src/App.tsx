import { useState } from 'react';
import Dashboard from './components/Dashboard';
import JobTable from './components/JobTable';
import ExecutionLogs from './components/ExecutionLogs';

type Tab = 'dashboard' | 'jobs' | 'logs';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('dashboard');

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg-primary)' }}>
      {/* ── Header ──────────────────────────────────────────────── */}
      <header style={{
        background: 'var(--bg-secondary)',
        borderBottom: '1px solid var(--border-subtle)',
        padding: '0 32px',
        position: 'sticky',
        top: 0,
        zIndex: 50,
      }}>
        <div style={{
          maxWidth: '1400px',
          margin: '0 auto',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          height: '64px',
        }}>
          {/* Logo */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, var(--accent-indigo), #8b5cf6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '1.1rem',
              boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
            }}>
              ⚡
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: '1rem', color: 'var(--text-primary)', lineHeight: 1.2 }}>
                Task Scheduler
              </div>
              <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', letterSpacing: '0.08em', textTransform: 'uppercase' }}>
                Quartz Admin v2.0
              </div>
            </div>
          </div>

          {/* Tab Navigation */}
          <nav style={{ display: 'flex', height: '100%' }}>
            {([
              { key: 'dashboard' as Tab, label: '📊 Dashboard' },
              { key: 'jobs' as Tab, label: '⚙️ Jobs' },
              { key: 'logs' as Tab, label: '📋 Logs' },
            ]).map(({ key, label }) => (
              <button
                key={key}
                className={`tab-btn ${activeTab === key ? 'active' : ''}`}
                onClick={() => setActiveTab(key)}
              >
                {label}
              </button>
            ))}
          </nav>

          {/* Status indicator */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div className="pulse-dot" />
            <span style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)', fontWeight: 500 }}>
              Connected
            </span>
          </div>
        </div>
      </header>

      {/* ── Main Content ────────────────────────────────────────── */}
      <main style={{
        maxWidth: '1400px',
        margin: '0 auto',
        padding: '32px',
      }}>
        {activeTab === 'dashboard' && <Dashboard />}
        {activeTab === 'jobs' && <JobTable />}
        {activeTab === 'logs' && <ExecutionLogs />}
      </main>

      {/* ── Footer ──────────────────────────────────────────────── */}
      <footer style={{
        textAlign: 'center',
        padding: '24px',
        color: 'var(--text-muted)',
        fontSize: '0.7rem',
        letterSpacing: '0.05em',
      }}>
        Task Scheduler V2 — Powered by Quartz + Spring Boot + React
      </footer>
    </div>
  );
}
