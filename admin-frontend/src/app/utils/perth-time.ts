// Event times in Australia/Perth (UTC+8, no daylight saving). The API stores
// UTC; the admin forms edit Perth wall-clock values. Mirrors the public app's
// utils/perth-time.util.ts, trimmed to what the admin page needs.

const PERTH_OFFSET_MINUTES = 8 * 60;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function perthShifted(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return new Date(date.getTime() + PERTH_OFFSET_MINUTES * 60_000);
}

/** "Tue 8 Sep 2026, 4:00 pm" in Perth time. */
export function formatPerthDateTime(iso: string | null | undefined): string {
  const d = perthShifted(iso);
  if (!d) return '';
  const hour = d.getUTCHours();
  const hour12 = hour % 12 === 0 ? 12 : hour % 12;
  const period = hour < 12 ? 'am' : 'pm';
  return `${WEEKDAYS[d.getUTCDay()]} ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}, ${hour12}:${pad(d.getUTCMinutes())} ${period}`;
}

/** "YYYY-MM-DDTHH:mm" in Perth time, for a datetime-local input. */
export function toPerthInputValue(iso: string | null | undefined): string {
  const d = perthShifted(iso);
  if (!d) return '';
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}

/** A datetime-local value typed as Perth time, back to a UTC ISO string. Null when blank or invalid. */
export function fromPerthInputValue(value: string | null | undefined): string | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value.trim());
  if (!match) return null;
  const [, y, mo, d, h, mi, s] = match;
  const date = new Date(Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi) - PERTH_OFFSET_MINUTES, Number(s ?? 0)));
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
