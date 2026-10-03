const TOKEN_KEY = 'visearch.accessToken';

export function getAccessToken(): string | null {
  return typeof window === 'undefined' ? null : window.sessionStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  window.sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(TOKEN_KEY);
}
