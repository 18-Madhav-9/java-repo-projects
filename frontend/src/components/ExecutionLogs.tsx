import { useEffect, useState, useCallback } from 'react';
import { jobApi, type ExecutionLog } from '../api/jobApi';

export default function ExecutionLogs() {
  const [logs, setLogs] = useState<ExecutionLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const fetchLogs = useCallback(async () => {
    try {
      const res = await jobApi.getLogs(page, 15);
      setLogs(res.data.logs);
      setTotalPages(res.data.totalPages);
      setTotalElements(res.data.totalElements);
    } catch (err) {
      console.error('Failed to fetch logs:', err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchLogs();
    const interval = setInterval(fetchLogs, 5000);
    return () => clearInterval(interval);
  }, [fetchLogs]);

  const formatDateTime = (dt?: string) => {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleString('en-US', {
      month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '60px' }}>
        <div className="spinner" />
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Execution Logs</h2>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
          {totalElements} total execution{totalElements !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="glass-card" style={{ overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Job</th>
                <th>Status</th>
                <th>Start Time</th>
                <th>Duration</th>
                <th>Retries</th>
                <th>Error</th>
              </tr>
            </thead>
            <tbody>
              {logs.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    No execution logs yet. Jobs will appear here after they run.
                  </td>
                </tr>
              ) : (
                logs.map((logEntry) => (
                  <tr key={logEntry.id}>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      #{logEntry.id}
                    </td>
                    <td>
                      <div style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
                        {logEntry.jobName}
                      </div>
                      <div style={{ color: 'var(--text-muted)', fontSize: '0.7rem' }}>
                        {logEntry.jobGroup}
                      </div>
                    </td>
                    <td>
                      <span className={`badge ${logEntry.status === 'SUCCESS' ? 'badge-success' : 'badge-failed'}`}>
                        {logEntry.status}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.8rem' }}>
                      {formatDateTime(logEntry.startTime)}
                    </td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {logEntry.executionDurationMs != null ? `${logEntry.executionDurationMs}ms` : '—'}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      {logEntry.retryCount > 0 ? (
                        <span className="badge badge-paused">{logEntry.retryCount}</span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>0</span>
                      )}
                    </td>
                    <td>
                      {logEntry.errorMessage ? (
                        <span
                          style={{
                            color: 'var(--accent-rose)',
                            fontSize: '0.75rem',
                            maxWidth: '200px',
                            display: 'inline-block',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                          title={logEntry.errorMessage}
                        >
                          {logEntry.errorMessage}
                        </span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>—</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            gap: '12px',
            padding: '16px',
            borderTop: '1px solid var(--border-subtle)',
          }}>
            <button
              className="btn btn-resume"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Prev
            </button>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Page {page + 1} of {totalPages}
            </span>
            <button
              className="btn btn-resume"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
