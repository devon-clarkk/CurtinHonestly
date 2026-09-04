import { describe, expect, it } from 'vitest';
import {
  formatPerthDate,
  formatPerthDateTime,
  formatPerthRange,
  formatPerthTime,
  fromPerthInputValue,
  perthDateKey,
  perthWeekLabel,
  perthWeekStartKey,
  toPerthInputValue
} from './perth-time.util';

// 2026-09-08 is a Tuesday. 08:00Z is 4:00 pm in Perth (UTC+8, no daylight saving).

describe('formatPerthDateTime', () => {
  it('renders a UTC instant as Perth wall-clock time', () => {
    expect(formatPerthDateTime('2026-09-08T08:00:00Z')).toBe('Tue 8 Sep, 4:00 pm');
  });

  it('crosses midnight into the next Perth day', () => {
    expect(formatPerthDateTime('2026-09-08T17:30:00Z')).toBe('Wed 9 Sep, 1:30 am');
  });

  it('uses 12 for noon and midnight', () => {
    expect(formatPerthDateTime('2026-09-08T04:00:00Z')).toBe('Tue 8 Sep, 12:00 pm');
    expect(formatPerthDateTime('2026-09-08T16:05:00Z')).toBe('Wed 9 Sep, 12:05 am');
  });

  it('can include the year', () => {
    expect(formatPerthDateTime('2026-09-08T08:00:00Z', true)).toBe('Tue 8 Sep 2026, 4:00 pm');
    expect(formatPerthDate('2026-12-31T20:00:00Z', true)).toBe('Fri 1 Jan 2027');
  });

  it('is empty for missing or invalid input', () => {
    expect(formatPerthDateTime(null)).toBe('');
    expect(formatPerthDateTime('')).toBe('');
    expect(formatPerthDateTime('not a date')).toBe('');
    expect(formatPerthTime(undefined)).toBe('');
  });
});

describe('formatPerthRange', () => {
  it('collapses a same-day end to a time', () => {
    expect(formatPerthRange('2026-09-08T08:00:00Z', '2026-09-08T09:30:00Z')).toBe('Tue 8 Sep, 4:00 pm to 5:30 pm');
  });

  it('spells out an end on another day', () => {
    expect(formatPerthRange('2026-09-08T08:00:00Z', '2026-09-09T01:00:00Z')).toBe('Tue 8 Sep, 4:00 pm to Wed 9 Sep, 9:00 am');
  });

  it('falls back to the start alone without an end', () => {
    expect(formatPerthRange('2026-09-08T08:00:00Z', null)).toBe('Tue 8 Sep, 4:00 pm');
  });
});

describe('week grouping', () => {
  it('keys a day and its Monday in Perth time', () => {
    expect(perthDateKey('2026-09-08T17:30:00Z')).toBe('2026-09-09');
    expect(perthWeekStartKey('2026-09-08T08:00:00Z')).toBe('2026-09-07');
    // Sunday night in Perth still belongs to the week that started the previous Monday.
    expect(perthWeekStartKey('2026-09-13T15:00:00Z')).toBe('2026-09-07');
    // A minute later it is Monday in Perth.
    expect(perthWeekStartKey('2026-09-13T16:00:00Z')).toBe('2026-09-14');
  });

  it('labels weeks relative to now', () => {
    const now = new Date('2026-09-04T04:00:00Z'); // Friday 4 Sep, Perth week of Mon 31 Aug
    expect(perthWeekLabel('2026-08-31', now)).toBe('This week');
    expect(perthWeekLabel('2026-09-07', now)).toBe('Next week');
    expect(perthWeekLabel('2026-09-21', now)).toBe('Week of 21 Sep');
    expect(perthWeekLabel('2027-01-04', now)).toBe('Week of 4 Jan 2027');
    expect(perthWeekLabel('', now)).toBe('');
  });
});

describe('datetime-local conversion', () => {
  it('turns a UTC instant into a Perth input value and back', () => {
    expect(toPerthInputValue('2026-09-08T08:00:00Z')).toBe('2026-09-08T16:00');
    expect(fromPerthInputValue('2026-09-08T16:00')).toBe('2026-09-08T08:00:00.000Z');
  });

  it('handles early-morning Perth times that fall on the previous UTC day', () => {
    expect(fromPerthInputValue('2026-09-09T01:30')).toBe('2026-09-08T17:30:00.000Z');
    expect(toPerthInputValue('2026-09-08T17:30:00Z')).toBe('2026-09-09T01:30');
  });

  it('accepts seconds and rejects garbage', () => {
    expect(fromPerthInputValue('2026-09-08T16:00:30')).toBe('2026-09-08T08:00:30.000Z');
    expect(fromPerthInputValue('')).toBeNull();
    expect(fromPerthInputValue(null)).toBeNull();
    expect(fromPerthInputValue('yesterday')).toBeNull();
    expect(toPerthInputValue(null)).toBe('');
  });
});
