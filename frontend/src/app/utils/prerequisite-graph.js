/**
 * Inverting the prerequisite graph: given "COMP3001 requires COMP1002", answer
 * "COMP1002 is required for COMP3001". No handbook page states that, and it is
 * the only fact worth reading on the 1,729 units nobody has reviewed.
 *
 * Plain JavaScript with a hand-written declaration file because two runtimes
 * need the same code and cannot share TypeScript: scripts/fetch-unit-codes.js
 * builds the map under Node before `ng build` exists, while the spec and the
 * app read it through prerequisite-graph.d.ts.
 */

/**
 * The shape of a code that has a page behind it, mirroring unitCodeForUrl in
 * faculty.util.ts. prerequisite-graph.spec.ts asserts the two agree, because
 * the app resolves a link with one and this builds the graph with the other.
 */
const UNIT_CODE_PATTERN = /^([A-Z]{2,5}\d{4})(?:V\d+)?$/;

/** `COMP1002v1` to `COMP1002`. Undefined for anything that is not a unit code. */
function resolveUnitCode(code) {
  if (typeof code !== 'string') {
    return undefined;
  }
  const match = code.trim().toUpperCase().match(UNIT_CODE_PATTERN);
  return match ? match[1] : undefined;
}

/**
 * Builds `prerequisite code -> the codes that require it`, sorted, from every
 * unit's own prerequisite list.
 *
 * `catalogueCodes` is the authority on what exists, and it does more work than
 * the pattern: 380 of the prerequisite codes in the live handbook data are
 * shaped like unit codes (ACCT1000, AGRI5000v1) and have no page, because they
 * name units the catalogue has since retired. Shape alone would put several
 * hundred dead links on the site. An edge survives only when both ends resolve
 * to a code the catalogue actually serves.
 *
 * The resulting key set carries a second guarantee the unit page relies on: a
 * prerequisite that resolves into the catalogue is, by construction, a key
 * here, since it is a prerequisite of at least the unit listing it.
 */
function invertPrerequisiteGraph(units, catalogueCodes) {
  const known = new Set();
  for (const code of catalogueCodes) {
    const resolved = resolveUnitCode(code);
    if (resolved) {
      known.add(resolved);
    }
  }

  const requiredFor = new Map();

  for (const unit of units) {
    const dependent = resolveUnitCode(unit && unit.code);
    if (!dependent || !known.has(dependent)) {
      continue;
    }

    const seen = new Set();
    for (const raw of (unit && unit.prerequisiteCodes) || []) {
      const prerequisite = resolveUnitCode(raw);
      if (!prerequisite || !known.has(prerequisite) || seen.has(prerequisite)) {
        continue;
      }
      // A unit listing itself would render as "required for itself", which
      // reads as a broken page rather than as the bad import it is.
      if (prerequisite === dependent) {
        continue;
      }
      seen.add(prerequisite);
      const dependents = requiredFor.get(prerequisite);
      if (dependents) {
        dependents.push(dependent);
      } else {
        requiredFor.set(prerequisite, [dependent]);
      }
    }
  }

  const sorted = {};
  for (const prerequisite of [...requiredFor.keys()].sort()) {
    sorted[prerequisite] = requiredFor.get(prerequisite).sort();
  }
  return sorted;
}

module.exports = { resolveUnitCode, invertPrerequisiteGraph };
