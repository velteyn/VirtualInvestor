/**
 * Friendly labels for request failures. The edge's default error body carries
 * status only (no message), so a raw failure reads
 * `POST /api/players/login failed (401): {...}` — never user-facing. This
 * helper maps the status to a label; call sites add context (login vs admin
 * login vs join). Errors the UI raises itself (validation) already read well
 * and pass through untouched.
 */
export type StatusLabels = Partial<Record<number, string>>;

const GENERIC: Record<number, string> = {
  400: 'Bad request — check the form and try again.',
  401: 'Not authorized — check the credentials.',
  403: 'Forbidden.',
  404: 'Not found — it may have ended.',
  409: 'Already exists.',
};

export function friendlyError(
  cause: unknown,
  labels: StatusLabels = {},
  fallback = 'Request failed — try again.',
): string {
  if (cause instanceof Error) {
    if (/fetch failed|Failed to fetch|NetworkError|Load failed/i.test(cause.message)) {
      return 'Network error — is the server running?';
    }
    const match = cause.message.match(/failed \((\d{3})\)/);
    if (match !== null) {
      const status = Number(match[1]);
      const label = labels[status];
      if (label !== undefined) {
        return label;
      }
      const generic = GENERIC[status];
      if (generic !== undefined) {
        return generic;
      }
      return fallback;
    }
    // Local validation errors are already phrased for the player.
    return cause.message;
  }
  return fallback;
}

/**
 * Labels for socket `error` frames. The `rejected` code carries the server's
 * own reason (already phrased); session codes map to labels so the ticket
 * never shows a bare code.
 */
export function socketErrorLabel(payload: { [key: string]: unknown }): string {
  const code = typeof payload.code === 'string' ? payload.code : '';
  if (code === 'not-approved') {
    return 'Account not approved yet — wait for the game master.';
  }
  if (code === 'not_joined') {
    return 'Not joined to this round — rejoining…';
  }
  if (code === 'resync_failed') {
    return 'Resync failed — retrying…';
  }
  if (typeof payload.message === 'string' && payload.message !== '') {
    return payload.message;
  }
  return 'Request failed — try again.';
}
