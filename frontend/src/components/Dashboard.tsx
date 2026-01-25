import { useEffect, useState, useCallback } from 'react';
import { jobApi, type ExecutionSummary } from '../api/jobApi';

export default function Dashboard() {
  const [summary, setSummary] = useState<ExecutionSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchSummary = useCallback(async () => {
    try {
      const res = await jobApi.getSummary();
      setSummary(res.data);
    } catch (err) {
      console.error('Failed to fetch summary:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSummary();
    const interval = setInterval(fetchSummary, 5000);
    return () => clearInterval(interval);
  }, [fetchSummary]);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '60px' }}>
        <div className="spinner" />
      </div>
    );
  }

  const stats = [
    {
      label: 'Total Executions',
      value: summary?.totalExecutions ?? 0,
      color: 'var(--accent-indigo)',
      icon: '⚡',
    },
    {
      label: 'Successful',
      value: summary?.successCount ?? 0,
      color: 'var(--accent-emerald)',
      icon: '✓',
    },
    {
      label: 'Failed',
      value: summary?.failedCount ?? 0,
      color: 'var(--accent-rose)',
      icon: '✗',
    },
    {
      label: 'Success Rate',
      value: summary?.successRate ?? 'N/A',
      color: 'var(--accent-sky)',
      icon: '📊',
    },
    {
      label: 'Avg Duration',
      value: summary?.averageDurationMs ? `${summary.averageDurationMs}ms` : '0ms',
      color: 'var(--accent-amber)',
      icon: '⏱',
    },
  ];

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: '32px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
          <div className="pulse-dot" />
          <span style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)', fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase' }}>
            Live — Refreshing every 5s
          </span>
        </div>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--text-primary)' }}>
          Execution Overview
        </h2>
      </div>

      {/* Stats Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: '16px',
      }}>
        {stats.map((stat) => (
          <div key={stat.label} className="glass-card stat-card">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
              <span style={{ fontSize: '1.25rem' }}>{stat.icon}</span>
            </div>
            <div className="stat-value" style={{ color: stat.color }}>
              {stat.value}
            </div>
            <div className="stat-label">{stat.label}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
