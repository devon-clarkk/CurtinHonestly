import { describe, expect, it } from 'vitest';
import { unitCodeForUrl } from './faculty.util';
import { invertPrerequisiteGraph, resolveUnitCode } from './prerequisite-graph';

const CATALOGUE = ['COMP1002', 'COMP2003', 'COMP3001', 'COMP3002', 'MATH1017', 'ACTL1002'];

describe('resolveUnitCode', () => {
  /**
   * The app resolves a prerequisite link with unitCodeForUrl and the build
   * script builds the graph with resolveUnitCode. Two implementations exist
   * only because Node cannot import TypeScript, so pin them to each other on
   * every shape the live handbook data actually contains.
   */
  it('agrees with unitCodeForUrl on every shape the handbook sends', () => {
    const samples = [
      'COMP1002',
      'COMP1002v1',
      'COMP1002V1',
      'comp1002v12',
      '  COMP1002v1  ',
      'STAT5003v2',
      'INDH1006',
      '1920',
      '305229',
      'COMP1002v',
      'COMP100',
      'C1002',
      'TOOLONG1002',
      '',
    ];

    for (const sample of samples) {
      expect(resolveUnitCode(sample), sample).toBe(unitCodeForUrl(sample));
    }

    expect(resolveUnitCode(undefined)).toBe(unitCodeForUrl(undefined));
  });

  it('strips the version suffix and drops anything that is not a unit code', () => {
    expect(resolveUnitCode('COMP1002v1')).toBe('COMP1002');
    expect(resolveUnitCode('comp1002')).toBe('COMP1002');
    expect(resolveUnitCode('1920')).toBeUndefined();
    expect(resolveUnitCode(null)).toBeUndefined();
  });
});

describe('invertPrerequisiteGraph', () => {
  it('inverts an edge so the prerequisite lists what needs it', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'COMP3001', prerequisiteCodes: ['COMP1002v1'] }],
      CATALOGUE
    );

    expect(map).toEqual({ COMP1002: ['COMP3001'] });
  });

  it('sorts both the keys and each list, so the file is stable across builds', () => {
    const map = invertPrerequisiteGraph(
      [
        { code: 'COMP3002', prerequisiteCodes: ['MATH1017', 'COMP1002'] },
        { code: 'COMP2003', prerequisiteCodes: ['COMP1002'] },
      ],
      CATALOGUE
    );

    expect(Object.keys(map)).toEqual(['COMP1002', 'MATH1017']);
    expect(map['COMP1002']).toEqual(['COMP2003', 'COMP3002']);
  });

  it('drops legacy numeric course identifiers', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'COMP3001', prerequisiteCodes: ['1920', '305229'] }],
      CATALOGUE
    );

    expect(map).toEqual({});
  });

  it('drops codes shaped like units that the catalogue does not serve', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'COMP3001', prerequisiteCodes: ['ACCT1000', 'AGRI5000v1'] }],
      CATALOGUE
    );

    expect(map).toEqual({});
  });

  it('drops a unit whose own code is not in the catalogue', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'ACCT1000', prerequisiteCodes: ['COMP1002'] }],
      CATALOGUE
    );

    expect(map).toEqual({});
  });

  it('records a unit once when two groups both name it', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'COMP3001', prerequisiteCodes: ['COMP1002v1', 'COMP1002', 'comp1002v2'] }],
      CATALOGUE
    );

    expect(map['COMP1002']).toEqual(['COMP3001']);
  });

  it('never lets a unit require itself', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'COMP3001', prerequisiteCodes: ['COMP3001v1', 'COMP1002'] }],
      CATALOGUE
    );

    expect(map).toEqual({ COMP1002: ['COMP3001'] });
  });

  it('leaves a unit that unlocks nothing out of the map entirely', () => {
    const map = invertPrerequisiteGraph(
      [
        { code: 'COMP3001', prerequisiteCodes: ['COMP1002'] },
        { code: 'ACTL1002', prerequisiteCodes: [] },
      ],
      CATALOGUE
    );

    expect(map['ACTL1002']).toBeUndefined();
    expect(map['COMP3001']).toBeUndefined();
  });

  it('tolerates a unit with no prerequisite list at all', () => {
    const map = invertPrerequisiteGraph(
      [{ code: 'ACTL1002' } as unknown as { code: string; prerequisiteCodes: string[] }],
      CATALOGUE
    );

    expect(map).toEqual({});
  });
});
