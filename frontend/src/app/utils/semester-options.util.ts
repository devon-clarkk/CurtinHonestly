/**
 * Generates the "Semester Taken" dropdown options relative to today instead of
 * a hardcoded list that goes stale (quick-fixes.md #4). Curtin's academic
 * calendar: Semester 1 ~Feb-Jun, Semester 2 ~Jul-Oct, Summer ~Nov-Feb
 * (labelled "Summer, YYYY/YY+1").
 */
type Term = { type: 'S1' | 'S2' | 'SUMMER'; year: number };

function currentTerm(now: Date): Term {
  const month = now.getMonth(); // 0-indexed
  const year = now.getFullYear();
  if (month >= 1 && month <= 5) return { type: 'S1', year };
  if (month >= 6 && month <= 9) return { type: 'S2', year };
  if (month >= 10) return { type: 'SUMMER', year: year + 1 };
  return { type: 'SUMMER', year }; // January: still the summer that started last November
}

function previousTerm(term: Term): Term {
  if (term.type === 'S2') return { type: 'S1', year: term.year };
  if (term.type === 'S1') return { type: 'SUMMER', year: term.year };
  return { type: 'S2', year: term.year - 1 };
}

function termLabel(term: Term): string {
  if (term.type === 'S1') return `Semester 1, ${term.year}`;
  if (term.type === 'S2') return `Semester 2, ${term.year}`;
  return `Summer, ${term.year - 1}/${String(term.year).slice(-2)}`;
}

/**
 * Oldest term offered explicitly. Six terms back from today only reached about
 * 18 months, so students reviewing a unit they took earlier in their degree had
 * nothing to pick. Anything older than this collapses into BEFORE_EARLIEST_OPTION.
 *
 * The list grows by three entries a year. If it ever gets unwieldy, move this
 * floor forward rather than reverting to a fixed count - a count goes stale at
 * the recent end, which is the bug this util was written to fix.
 */
const EARLIEST_TERM: Term = { type: 'S1', year: 2022 };

export const BEFORE_EARLIEST_OPTION = `Before ${EARLIEST_TERM.year}`;

/** Ordering within a year: Summer (Nov-Feb) comes before S1, which comes before S2. */
function termRank(term: Term): number {
  if (term.type === 'SUMMER') return 0;
  return term.type === 'S1' ? 1 : 2;
}

function isOlderThan(a: Term, b: Term): boolean {
  if (a.year !== b.year) return a.year < b.year;
  return termRank(a) < termRank(b);
}

/**
 * The raw term sequence, newest first. Exported for tests; production code wants
 * generateSemesterOptions below.
 */
export function generateRecentTerms(count: number, referenceDate: Date = new Date()): string[] {
  const options: string[] = [];
  let term = currentTerm(referenceDate);
  for (let i = 0; i < count; i++) {
    options.push(termLabel(term));
    term = previousTerm(term);
  }
  return options;
}

/**
 * Every term from the current one back to EARLIEST_TERM, then a catch-all for
 * anything older.
 */
export function generateSemesterOptions(referenceDate: Date = new Date()): string[] {
  const options: string[] = [];
  let term = currentTerm(referenceDate);

  while (!isOlderThan(term, EARLIEST_TERM)) {
    options.push(termLabel(term));
    term = previousTerm(term);
  }

  options.push(BEFORE_EARLIEST_OPTION);
  return options;
}
