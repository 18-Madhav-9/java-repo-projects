import { useEffect, useState, useCallback } from 'react';
import { jobApi, type QuartzJob } from '../api/jobApi';

export default function JobTable() {
  const [jobs, setJobs] = useState<QuartzJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const fetchJobs = useCallback(async () => {
    try {
      const res = await jobApi.listJobs();
      setJobs(res.data);
    } catch (err) {
      console.error('Failed to fetch jobs:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchJobs();
    const interval = setInterval(fetchJobs, 5000);
    return () => clearInterval(interval);
  }, [fetchJobs]);

  const handleAction = async (
    action: 'trigger' | 'pause' | 'resume' | 'delete',
    group: string,
    name: string
  ) => {
    const key = `${action}-${group}-${name}`;
    setActionLoading(key);
    try {
      switch (action) {
        case 'trigger':
          await jobApi.triggerJob(group, name);
          break;
        case 'pause':
          await jobApi.pauseJob(group, name);
          break;
        case 'resume':
          await jobApi.resumeJob(group, name);
          break;
        case 'delete':
          if (confirm(`Delete job "${name}"? This cannot be undone.`)) {
            await jobApi.deleteJob(group, name);
          }
          break;
      }
      await fetchJobs();
    } catch (err) {
      console.error(`Action ${action} failed:`, err);
    } finally {
      setActionLoading(null);
    }
  };

  const formatDateTime = (dt?: string) => {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleString('en-US', {
      month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  };

  const getStateBadge = (state: string) => {
    const cls = state === 'NORMAL' ? 'badge-normal'
      : state === 'PAUSED' ? 'badge-paused'
      : state === 'BLOCKED' ? 'badge-failed'
      : 'badge-none';
    return <span className={`badge ${cls}`}>{state}</span>;
  };

  const getJobTypeIcon = (jobClass: string) => {
    if (jobClass.includes('Email')) return '📧';
    if (jobClass.includes('Pdf')) return '📄';
    if (jobClass.includes('Kafka')) return '📡';
    return '⚙️';
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
        <h2 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Scheduled Jobs</h2>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
          {jobs.length} job{jobs.length !== 1 ? 's' : ''} registered
        </span>
      </div>

      <div className="glass-card" style={{ overflow: 'hidden' }}>
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Job</th>
                <th>Type</th>
                <th>Status</th>
                <th>Cron</th>
                <th>Next Run</th>
                <th>Last Run</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {jobs.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)' }}>
                    No jobs found. Create one to get started.
                  </td>
                </tr>
              ) : (
                jobs.map((job) => (
                  <tr key={`${job.group}-${job.name}`}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <span style={{ fontSize: '1.25rem' }}>{getJobTypeIcon(job.jobClass)}</span>
                        <div>
                          <div style={{ color: 'var(--text-primary)', fontWeight: 600, fontSize: '0.875rem' }}>
                            {job.name}
                          </div>
                          <div style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>
                            {job.description || job.group}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--accent-sky)' }}>
                        {job.jobClass}
                      </span>
                    </td>
                    <td>{getStateBadge(job.state)}</td>
                    <td>
                      <code style={{ fontSize: '0.75rem', color: 'var(--text-muted)', background: 'rgba(255,255,255,0.04)', padding: '2px 8px', borderRadius: '4px' }}>
                        {job.cronExpression || '—'}
                      </code>
                    </td>
                    <td style={{ fontSize: '0.8rem' }}>
                      {formatDateTime(job.nextFireTime)}
                    </td>
                    <td style={{ fontSize: '0.8rem' }}>
                      {formatDateTime(job.previousFireTime)}
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                        <button
                          className="btn btn-trigger"
                          disabled={actionLoading !== null}
                          onClick={() => handleAction('trigger', job.group, job.name)}
                        >
                          {actionLoading === `trigger-${job.group}-${job.name}` ? '...' : '▶ Run'}
                        </button>

                        {job.state === 'PAUSED' ? (
                          <button
                            className="btn btn-resume"
                            disabled={actionLoading !== null}
                            onClick={() => handleAction('resume', job.group, job.name)}
                          >
                            {actionLoading === `resume-${job.group}-${job.name}` ? '...' : '▶ Resume'}
                          </button>
                        ) : (
                          <button
                            className="btn btn-pause"
                            disabled={actionLoading !== null}
                            onClick={() => handleAction('pause', job.group, job.name)}
                          >
                            {actionLoading === `pause-${job.group}-${job.name}` ? '...' : '⏸ Pause'}
                          </button>
                        )}

                        <button
                          className="btn btn-delete"
                          disabled={actionLoading !== null}
                          onClick={() => handleAction('delete', job.group, job.name)}
                        >
                          🗑
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
