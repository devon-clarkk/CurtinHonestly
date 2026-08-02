/**
 * Reviews get written in the ~2-week window right after results release —
 * ~late June and ~late November in Australia (catalog-and-growth.md #4).
 * That's the only moment most students are motivated to write one.
 */
export function isResultsSeasonWindow(referenceDate: Date = new Date()): boolean {
  const month = referenceDate.getMonth(); // 0-indexed
  const day = referenceDate.getDate();

  const inJuneWindow = (month === 5 && day >= 25) || (month === 6 && day <= 9);
  const inNovemberWindow = (month === 10 && day >= 25) || (month === 11 && day <= 9);

  return inJuneWindow || inNovemberWindow;
}
