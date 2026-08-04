/**
 * The "Semester Taken" dropdown, generated relative to today rather than from a
 * hardcoded list that goes stale (quick-fixes.md #4). Curtin's academic calendar:
 * Semester 1 ~Feb-Jun, Semester 2 ~Jul-Oct, Summer ~Nov-Feb (labelled
 * "Summer, YYYY/YY+1").
 *
 * Options carry a structured (termType, termYear) value and a separate display
 * label. The API stores the pair, never the label - a stored label cannot be
 * sorted or aggregated, and freezes today's wording into the database.
 */
import { AcademicTerm } from '../models/unit.model';

type Term = { type: 'S1' | 'S2' | 'SUMMER'; year: number };

export interface SemesterOption {
  /** What gets sent to the API. Year is null for the open-ended earlier bucket. */
  termType: AcademicTerm;
  termYear: number | null;
  label: string;
}

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

function toApiTerm(term: Term): AcademicTerm {
  if (term.type === 'S1') return 'SEMESTER_1';
  if (term.type === 'S2') return 'SEMESTER_2';
  return 'SUMMER';
}

/**
 * Oldest term offered explicitly. Six terms back from today only reached about
 * 18 months, so students reviewing a unit from earlier in their degree had
 * nothing to pick. Anything older collapses into EARLIER_UNSPECIFIED.
 *
 * The list grows by three entries a year. If it ever gets unwieldy, move this
 * floor forward rather than reverting to a fixed count - a count goes stale at
 * the recent end, which is the bug this util was written to fix.
 */
const EARLIEST_TERM: Term = { type: 'S1', year: 2022 };

export const BEFORE_EARLIEST_OPTION: SemesterOption = {
  termType: 'EARLIER_UNSPECIFIED',
  termYear: null,
  label: `Before ${EARLIEST_TERM.year}`,
};

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
export function generateRecentTerms(count: number, referenceDate: Date = new Date()): SemesterOption[] {
  const options: SemesterOption[] = [];
  let term = currentTerm(referenceDate);
  for (let i = 0; i < count; i++) {
    options.push({ termType: toApiTerm(term), termYear: term.year, label: termLabel(term) });
    term = previousTerm(term);
  }
  return options;
}

/**
 * Every term from the current one back to EARLIEST_TERM, then a catch-all for
 * anything older.
 */
export function generateSemesterOptions(referenceDate: Date = new Date()): SemesterOption[] {
  const options: SemesterOption[] = [];
  let term = currentTerm(referenceDate);

  while (!isOlderThan(term, EARLIEST_TERM)) {
    options.push({ termType: toApiTerm(term), termYear: term.year, label: termLabel(term) });
    term = previousTerm(term);
  }

  options.push(BEFORE_EARLIEST_OPTION);
  return options;
}

/**
 * Display label for a term stored on a review. Used wherever a review is shown,
 * so the wording lives here rather than in the database.
 */
export function formatTerm(termType: AcademicTerm | null | undefined, termYear: number | null | undefined): string {
  if (!termType) return '';
  if (termType === 'EARLIER_UNSPECIFIED') return BEFORE_EARLIEST_OPTION.label;
  if (termYear == null) return '';

  if (termType === 'SEMESTER_1') return termLabel({ type: 'S1', year: termYear });
  if (termType === 'SEMESTER_2') return termLabel({ type: 'S2', year: termYear });
  return termLabel({ type: 'SUMMER', year: termYear });
}

/**
 * Sort key for a stored term, newest first when sorted descending.
 *
 * EARLIER_UNSPECIFIED and unanswered terms return null: they have no position on
 * a timeline and must not be sorted as if they did.
 */
export function termSortKey(
  termType: AcademicTerm | null | undefined,
  termYear: number | null | undefined
): number | null {
  if (!termType || termType === 'EARLIER_UNSPECIFIED' || termYear == null) return null;
  const rank = termType === 'SUMMER' ? 0 : termType === 'SEMESTER_1' ? 1 : 2;
  return termYear * 3 + rank;
}
