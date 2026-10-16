const DEFAULT_RETURN_URL = '/lobby';

/** Accepts only same-app absolute paths; anything else (external, protocol-relative, /auth) falls back to the lobby. */
export function safeReturnUrl(url: string | null): string {
  if (!url || !url.startsWith('/') || url.startsWith('//') || url.startsWith('/\\')) {
    return DEFAULT_RETURN_URL;
  }
  if (url === '/auth' || url.startsWith('/auth?')) {
    return DEFAULT_RETURN_URL;
  }
  return url;
}
