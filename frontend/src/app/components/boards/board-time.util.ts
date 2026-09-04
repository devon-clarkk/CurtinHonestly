/**
 * "3 minutes ago" style labels for board timestamps. Boards are about what is
 * being discussed now, so relative time reads better than a date; the full
 * date goes in the title attribute for anyone who wants it.
 */
export function timeAgo(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) {
    return '';
  }
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) {
    return '';
  }
  const seconds = Math.max(0, Math.round((now.getTime() - then) / 1000));
  if (seconds < 60) {
    return 'just now';
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`;
  }
  const days = Math.round(hours / 24);
  if (days < 30) {
    return `${days} ${days === 1 ? 'day' : 'days'} ago`;
  }
  const months = Math.round(days / 30);
  if (months < 12) {
    return `${months} ${months === 1 ? 'month' : 'months'} ago`;
  }
  const years = Math.round(days / 365);
  return `${years} ${years === 1 ? 'year' : 'years'} ago`;
}

export function fullDate(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toLocaleString('en-AU', { dateStyle: 'medium', timeStyle: 'short' });
}

/** Reads an API error body ({ error: string }) with a fallback for network failures. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const body = (err as { error?: { error?: string } } | null)?.error;
  return body && typeof body.error === 'string' && body.error ? body.error : fallback;
}
