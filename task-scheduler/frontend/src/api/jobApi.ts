import axiosClient from './axiosClient';

// ── Types ────────────────────────────────────────────────────────

export interface QuartzJob {
  name: string;
  group: string;
  description: string;
  jobClass: string;
  state: string;
  cronExpression?: string;
  nextFireTime?: string;
  previousFireTime?: string;
}

export interface ExecutionLog {
  id: number;
  jobName: string;
  jobGroup: string;
  status: string;
  startTime: string;
  endTime: string;
  executionDurationMs: number;
  retryCount: number;
  errorMessage?: string;
}

export interface ExecutionLogsResponse {
  logs: ExecutionLog[];
  currentPage: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
}

export interface ExecutionSummary {
  totalExecutions: number;
  successCount: number;
  failedCount: number;
  successRate: string;
  averageDurationMs: string;
}

export interface CreateJobRequest {
  name: string;
  type: string;
  group?: string;
  description?: string;
  cronExpression?: string;
}

// ── API Functions ────────────────────────────────────────────────

export const jobApi = {
  /** List all Quartz jobs */
  listJobs: () =>
    axiosClient.get<QuartzJob[]>('/jobs'),

  /** Create a new job */
  createJob: (data: CreateJobRequest) =>
    axiosClient.post('/jobs', data),

  /** Trigger a job to run immediately */
  triggerJob: (group: string, name: string) =>
    axiosClient.post(`/jobs/${group}/${name}/trigger`),

  /** Pause a job */
  pauseJob: (group: string, name: string) =>
    axiosClient.post(`/jobs/${group}/${name}/pause`),

  /** Resume a paused job */
  resumeJob: (group: string, name: string) =>
    axiosClient.post(`/jobs/${group}/${name}/resume`),

  /** Delete a job */
  deleteJob: (group: string, name: string) =>
    axiosClient.delete(`/jobs/${group}/${name}`),

  /** Get paginated execution logs */
  getLogs: (page = 0, size = 20) =>
    axiosClient.get<ExecutionLogsResponse>(`/jobs/logs?page=${page}&size=${size}`),

  /** Get execution summary stats */
  getSummary: () =>
    axiosClient.get<ExecutionSummary>('/jobs/logs/summary'),
};
