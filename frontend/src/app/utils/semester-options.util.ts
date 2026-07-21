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

export function generateSemesterOptions(count: number = 6, referenceDate: Date = new Date()): string[] {
  const options: string[] = [];
  let term = currentTerm(referenceDate);
  for (let i = 0; i < count; i++) {
    options.push(termLabel(term));
    term = previousTerm(term);
  }
  return options;
}
