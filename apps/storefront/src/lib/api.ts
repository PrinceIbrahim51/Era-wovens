'use client';
// Browser calls go to same-origin /api/v1/* which Next rewrites to the gateway, so the
// HttpOnly guest cookie is first-party and SameSite=Lax protects state changes.

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public body: Record<string, unknown> = {}) {
    super(message);
  }
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const { json, headers, ...rest } = init;
  let res: Response;
  try {
    res = await fetch(path, {
      ...rest,
      credentials: 'same-origin',
      headers: { Accept: 'application/json', ...(json !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
      body: json !== undefined ? JSON.stringify(json) : rest.body,
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'You appear to be offline. Check your connection and try again.');
  }
  if (res.status === 204 || res.status === 202) return undefined as T;
  const body = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  if (!res.ok) {
    const code = typeof body.code === 'string' ? body.code : res.status === 429 ? 'RATE_LIMITED' : 'ERROR';
    const message = res.status === 429
      ? 'Too many requests. Please wait a moment and try again.'
      : typeof body.detail === 'string' ? body.detail : 'Something went wrong. Please try again.';
    throw new ApiError(res.status, code, message, body);
  }
  return body as T;
}
