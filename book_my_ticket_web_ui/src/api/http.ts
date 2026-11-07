import { config } from '../config';

/** RFC 7807 problem+json as produced by GlobalExceptionHandler and the gateway fallback. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  [extra: string]: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: ProblemDetail;
  readonly retryAfterSeconds: number | null;

  constructor(status: number, problem: ProblemDetail, retryAfterSeconds: number | null) {
    super(problem.detail || problem.title || `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code ?? defaultCode(status);
    this.problem = problem;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  get isRateLimited(): boolean {
    return this.status === 429;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  /** Transient failures worth retrying automatically (network, gateway fallback, throttling). */
  get isTransient(): boolean {
    return this.status === 0 || this.status === 502 || this.status === 503 || this.status === 504;
  }
}

function defaultCode(status: number): string {
  if (status === 0) return 'NETWORK_ERROR';
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  if (status === 429) return 'RATE_LIMITED';
  if (status >= 500) return 'SERVER_ERROR';
  return 'REQUEST_FAILED';
}

/** Human-friendly message for an error thrown by the API layer (or anything else). */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isRateLimited) {
      const wait = error.retryAfterSeconds ? ` in ${error.retryAfterSeconds}s` : ' shortly';
      return `You're going a little fast. Please retry${wait}.`;
    }
    if (error.status === 0) return 'Cannot reach BMTickets. Check your connection or that the API gateway is running.';
    if (error.status === 403) return 'You do not have permission to do that.';
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}

type TokenProvider = () => Promise<string | null>;
let tokenProvider: TokenProvider = async () => null;
let onUnauthorized: () => void = () => {};

/** Wired by the AuthProvider so the HTTP layer stays framework-agnostic and testable. */
export function configureAuth(provider: TokenProvider, unauthorizedHandler: () => void): void {
  tokenProvider = provider;
  onUnauthorized = unauthorizedHandler;
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  auth?: boolean;
  signal?: AbortSignal;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, auth = false, signal } = options;
  const finalHeaders: Record<string, string> = { Accept: 'application/json, application/problem+json', ...headers };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  if (auth) {
    const token = await tokenProvider();
    if (token) finalHeaders.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${config.apiBaseUrl}${path}`, {
      method,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new ApiError(0, { detail: 'Network error' }, null);
  }

  if (!response.ok) {
    const problem = await parseProblem(response);
    const retryAfter = Number(response.headers.get('Retry-After'));
    if (response.status === 401 && auth) onUnauthorized();
    throw new ApiError(response.status, problem, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null);
  }

  if (response.status === 204 || response.status === 202) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function parseProblem(response: Response): Promise<ProblemDetail> {
  try {
    const text = await response.text();
    if (!text) return { status: response.status, title: response.statusText };
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === 'object' && parsed !== null ? (parsed as ProblemDetail) : { detail: String(parsed) };
  } catch {
    return { status: response.status, title: response.statusText };
  }
}
