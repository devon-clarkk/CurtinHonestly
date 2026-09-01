import { Injectable } from '@angular/core';
import { resolveUnitCode } from '../utils/prerequisite-graph';
import generatedRequiredFor from '../../generated/required-for.json';

/**
 * Which units a unit unlocks, read from the map scripts/fetch-unit-codes.js
 * inverts once per production build.
 *
 * No Curtin handbook page states this. A handbook page lists what a unit needs,
 * never what needs it, so the answer only exists after the whole catalogue has
 * been inverted. That is the one fact worth reading on the 1,729 units nobody
 * has reviewed, which is most of the site.
 *
 * A service rather than a bare import so a test can hand the page a graph.
 */
@Injectable({ providedIn: 'root' })
export class PrerequisiteGraphService {
  protected readonly requiredFor: Record<string, string[]> = generatedRequiredFor as Record<
    string,
    string[]
  >;

  /**
   * False on any build that did not fetch the catalogue: local development, and
   * the checked-in placeholder. The page has to tell "unlocks nothing" apart
   * from "was never asked", because only the first is a fact about the unit.
   *
   * Counted once. The template asks on every change-detection pass, and the
   * live map has 753 keys to walk.
   */
  private readonly available = Object.keys(this.requiredFor).length > 0;

  isAvailable(): boolean {
    return this.available;
  }

  /** The units that name this one as a prerequisite, sorted, never undefined. */
  unitsRequiring(code: string | undefined): string[] {
    const resolved = resolveUnitCode(code);
    return (resolved && this.requiredFor[resolved]) || [];
  }

  /**
   * Whether a prerequisite code has a page to link to.
   *
   * Shape is not enough: 380 of the prerequisite codes in the live handbook
   * data look exactly like unit codes (ACCT1000, AGRI5000v1) and have no page,
   * because they name units the catalogue has retired. The graph settles it
   * without shipping the catalogue to the browser, because being a key here
   * means the build found the code in the catalogue: a prerequisite is, by
   * construction, a prerequisite of at least the unit that lists it.
   */
  hasPage(code: string | undefined): boolean {
    const resolved = resolveUnitCode(code);
    return !!resolved && this.requiredFor[resolved] !== undefined;
  }
}
