export function extractInviteCode(input: string): string {
  if (!input) return '';
  const trimmed = input.trim();
  if (!trimmed) return '';

  const queryMatch = trimmed.match(/[?&](?:join|code|token)=([a-zA-Z0-9_-]+)/i);
  if (queryMatch) {
    return queryMatch[1];
  }

  const pathMatch = trimmed.match(/(?:\/game\/|\/match\/)([a-zA-Z0-9_-]+)/i);
  if (pathMatch) {
    return pathMatch[1];
  }

  if (trimmed.includes('/') || trimmed.includes(':')) {
    const cleaned = trimmed.split('?')[0].split('#')[0].replace(/\/+$/, '');
    const lastSegment = cleaned.substring(cleaned.lastIndexOf('/') + 1);
    if (lastSegment && /^[a-zA-Z0-9_-]+$/.test(lastSegment)) {
      return lastSegment;
    }
  }

  return trimmed.replace(/['"]/g, '');
}

export function buildInviteUrl(inviteToken: string): string {
  if (!inviteToken) return '';
  if (typeof window !== 'undefined' && window.location) {
    return `${window.location.origin}/game/${inviteToken}`;
  }
  return `/game/${inviteToken}`;
}
