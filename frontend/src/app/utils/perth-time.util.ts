/**
 * Event times for display and editing in Australia/Perth.
 *
 * The API stores and returns UTC instants. Every student reading the site is
 * on Perth time, so dates are rendered in that zone regardless of the
 * visitor's device, and the portal's datetime inputs are Perth wall-clock
 * values converted back to UTC on save.
 *
 * Perth (AWST) is UTC+8 all year with no daylight saving, which is what makes
 * the input conversion a fixed offset. Display goes through Intl with the
 * IANA zone so it stays correct even if that ever changed. Everything here
 * is pure and runs the same under SSR (Node) and in the browser.
 */

export const PERTH_TIME_ZONE = 'Australia/Perth';
const PERTH_OFFSET_MINUTES = 8 * 60;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

interface PerthParts {
  year: number;
  /** 1 to 12 */
  month: number;
  day: number;
  /** 0 to 23 */
  hour: number;
  minute: number;
  /** 0 = Sunday */
  weekday: number;
}

function toDate(iso: string | Date | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const date = iso instanceof Date ? iso : new Date(iso);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** The Perth wall-clock components of an instant. */
export function perthParts(iso: string | Date | null | undefined): PerthParts | null {
  const date = toDate(iso);
  if (!date) {
    return null;
  }
  // Intl with the IANA zone is the source of truth for display; the fixed
  // offset below is only the fallback for a runtime without time zone data.
  try {
    const formatter = new Intl.DateTimeFormat('en-AU', {
      timeZone: PERTH_TIME_ZONE,
      hourCycle: 'h23',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      weekday: 'short'
    });
    const parts: Record<string, string> = {};
    for (const part of formatter.formatToParts(date)) {
      parts[part.type] = part.value;
    }
    const weekday = WEEKDAYS.findIndex((w) => parts['weekday']?.startsWith(w));
    const hour = Number(parts['hour']) % 24;
    if (parts['year'] && parts['month'] && parts['day'] && weekday >= 0) {
      return {
        year: Number(parts['year']),
        month: Number(parts['month']),
        day: Number(parts['day']),
        hour,
        minute: Number(parts['minute']),
        weekday
      };
    }
  } catch {
    // Fall through to the fixed offset.
  }
  const shifted = new Date(date.getTime() + PERTH_OFFSET_MINUTES * 60_000);
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
    weekday: shifted.getUTCDay()
  };
}

function timeOf(p: PerthParts): string {
  const period = p.hour < 12 ? 'am' : 'pm';
  const hour12 = p.hour % 12 === 0 ? 12 : p.hour % 12;
  return `${hour12}:${String(p.minute).padStart(2, '0')} ${period}`;
}

function dateOf(p: PerthParts, withYear: boolean): string {
  const base = `${WEEKDAYS[p.weekday]} ${p.day} ${MONTHS[p.month - 1]}`;
  return withYear ? `${base} ${p.year}` : base;
}

/** "Tue 8 Sep" (add the year with `withYear` for dates far from now). */
export function formatPerthDate(iso: string | Date | null | undefined, withYear = false): string {
  const p = perthParts(iso);
  return p ? dateOf(p, withYear) : '';
}

/** "4:00 pm" */
export function formatPerthTime(iso: string | Date | null | undefined): string {
  const p = perthParts(iso);
  return p ? timeOf(p) : '';
}

/** "Tue 8 Sep, 4:00 pm" */
export function formatPerthDateTime(iso: string | Date | null | undefined, withYear = false): string {
  const p = perthParts(iso);
  return p ? `${dateOf(p, withYear)}, ${timeOf(p)}` : '';
}

/**
 * A start with an optional end: "Tue 8 Sep, 4:00 pm to 5:00 pm" on one day,
 * or "Tue 8 Sep, 4:00 pm to Wed 9 Sep, 9:00 am" across days.
 */
export function formatPerthRange(
  startIso: string | Date | null | undefined,
  endIso: string | Date | null | undefined
): string {
  const start = perthParts(startIso);
  if (!start) {
    return '';
  }
  const end = perthParts(endIso);
  if (!end) {
    return `${dateOf(start, false)}, ${timeOf(start)}`;
  }
  const sameDay = start.year === end.year && start.month === end.month && start.day === end.day;
  if (sameDay) {
    return `${dateOf(start, false)}, ${timeOf(start)} to ${timeOf(end)}`;
  }
  return `${dateOf(start, false)}, ${timeOf(start)} to ${dateOf(end, false)}, ${timeOf(end)}`;
}

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** "YYYY-MM-DD" of the Perth calendar day the instant falls on. */
export function perthDateKey(iso: string | Date | null | undefined): string {
  const p = perthParts(iso);
  return p ? `${p.year}-${pad(p.month)}-${pad(p.day)}` : '';
}

/** "YYYY-MM-DD" of the Monday that starts the Perth week the instant falls in. */
export function perthWeekStartKey(iso: string | Date | null | undefined): string {
  const p = perthParts(iso);
  if (!p) {
    return '';
  }
  const daysSinceMonday = (p.weekday + 6) % 7;
  const monday = new Date(Date.UTC(p.year, p.month - 1, p.day - daysSinceMonday));
  return `${monday.getUTCFullYear()}-${pad(monday.getUTCMonth() + 1)}-${pad(monday.getUTCDate())}`;
}

/** "This week", "Next week", or "Week of 21 Sep" for a week key from perthWeekStartKey. */
export function perthWeekLabel(weekStartKey: string, now: Date = new Date()): string {
  if (!weekStartKey) {
    return '';
  }
  const thisWeek = perthWeekStartKey(now);
  if (weekStartKey === thisWeek) {
    return 'This week';
  }
  const [y, m, d] = weekStartKey.split('-').map(Number);
  const [ty, tm, td] = thisWeek.split('-').map(Number);
  const diffDays = Math.round((Date.UTC(y, m - 1, d) - Date.UTC(ty, tm - 1, td)) / 86_400_000);
  if (diffDays === 7) {
    return 'Next week';
  }
  if (diffDays === -7) {
    return 'Last week';
  }
  const withYear = y !== ty;
  return `Week of ${d} ${MONTHS[m - 1]}${withYear ? ` ${y}` : ''}`;
}

/** "YYYY-MM-DDTHH:mm" in Perth time, the value a datetime-local input expects. */
export function toPerthInputValue(iso: string | Date | null | undefined): string {
  const p = perthParts(iso);
  return p ? `${p.year}-${pad(p.month)}-${pad(p.day)}T${pad(p.hour)}:${pad(p.minute)}` : '';
}

/**
 * A datetime-local value typed as Perth time, back to a UTC ISO string for the
 * API. Null for blank or unparsable input.
 */
export function fromPerthInputValue(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value.trim());
  if (!match) {
    return null;
  }
  const [, y, mo, d, h, mi, s] = match;
  const utc = Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi) - PERTH_OFFSET_MINUTES, Number(s ?? 0));
  const date = new Date(utc);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
