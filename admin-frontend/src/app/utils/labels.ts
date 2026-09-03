// Display labels for stored enum values. The backend deliberately sends the raw
// enum names; the wording lives here, matching the public site.

const TAG_LABELS: Record<string, string> = {
  GROUP_WORK: 'Group work',
  WEEKLY_QUIZZES: 'Weekly quizzes',
  ATTENDANCE_MARKED: 'Attendance marked',
  RECORDED_LECTURES: 'Recorded lectures',
  PROCTORED_EXAM: 'Proctored exam',
  HEAVY_READING: 'Heavy reading load',
  PRACTICAL_LABS: 'Practical labs/tutorials',
  OPEN_BOOK_EXAM: 'Open-book exam'
};

export function tagLabel(tag: string): string {
  return TAG_LABELS[tag] ?? tag.replace(/_/g, ' ').toLowerCase();
}

// Summer spans a year boundary and is stored under the year it ends in, so
// SUMMER/2026 reads "Summer, 2025/26".
export function termLabel(termType: string | null, termYear: number | null): string {
  if (!termType) return 'Not given';
  switch (termType) {
    case 'SEMESTER_1':
      return termYear ? `Semester 1, ${termYear}` : 'Semester 1';
    case 'SEMESTER_2':
      return termYear ? `Semester 2, ${termYear}` : 'Semester 2';
    case 'SUMMER':
      return termYear ? `Summer, ${termYear - 1}/${String(termYear).slice(-2)}` : 'Summer';
    case 'EARLIER_UNSPECIFIED':
      return 'Earlier';
    default:
      return termType;
  }
}

// Short axis label for charts: "S1 26", "S2 26", "Sum 26".
export function termShortLabel(termType: string, termYear: number | null): string {
  const yy = termYear ? String(termYear).slice(-2) : '';
  switch (termType) {
    case 'SEMESTER_1':
      return `S1 ${yy}`.trim();
    case 'SEMESTER_2':
      return `S2 ${yy}`.trim();
    case 'SUMMER':
      return `Sum ${yy}`.trim();
    default:
      return 'Earlier';
  }
}

export function percent(ratio: number, digits = 0): string {
  if (!Number.isFinite(ratio)) return '0%';
  return `${(ratio * 100).toFixed(digits)}%`;
}

// 1,284 / 12.9K / 1.2M, for stat tiles.
export function compactNumber(value: number): string {
  if (!Number.isFinite(value)) return '0';
  const abs = Math.abs(value);
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`;
  if (abs >= 10_000) return `${(value / 1_000).toFixed(1).replace(/\.0$/, '')}K`;
  return value.toLocaleString('en-AU');
}
