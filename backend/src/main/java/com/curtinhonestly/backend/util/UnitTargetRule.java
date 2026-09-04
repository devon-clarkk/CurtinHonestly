package com.curtinhonestly.backend.util;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.UnitLevel;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * How a row decides which units it belongs to. Shared by unit resources and
 * club events so both features answer "does this show on unit X?" the same way.
 *
 * Two modes:
 * <ul>
 *   <li>{@code targetUnitId} set: the row belongs to exactly that unit.</li>
 *   <li>{@code targetUnitId} null: the row is a rule. Every non-empty criterion
 *       ({@code prefixes}, {@code faculty}, {@code level}) must hold for a unit.
 *       A rule with no criteria matches every unit (site-wide).</li>
 * </ul>
 *
 * Pure and immutable so it can be unit tested without Spring or a database.
 */
public record UnitTargetRule(String targetUnitId, List<String> prefixes, Faculty faculty, UnitLevel level) {

    public UnitTargetRule {
        prefixes = prefixes == null ? List.of() : List.copyOf(prefixes);
    }

    public static UnitTargetRule forUnit(String unitId) {
        return new UnitTargetRule(unitId, List.of(), null, null);
    }

    public static UnitTargetRule rule(List<String> prefixes, Faculty faculty, UnitLevel level) {
        return new UnitTargetRule(null, prefixes, faculty, level);
    }

    /** Builds from stored columns: a raw comma separated prefix string and the two optional enums. */
    public static UnitTargetRule fromColumns(String targetUnitId, String rawPrefixes, Faculty faculty, UnitLevel level) {
        if (targetUnitId != null) {
            return forUnit(targetUnitId);
        }
        return rule(splitPrefixes(rawPrefixes), faculty, level);
    }

    public boolean unitSpecific() {
        return targetUnitId != null;
    }

    /** True when there is no unit and no criterion at all. */
    public boolean siteWide() {
        return targetUnitId == null && prefixes.isEmpty() && faculty == null && level == null;
    }

    public boolean matches(String unitId, String code, Faculty unitFaculty, UnitLevel unitLevel) {
        if (targetUnitId != null) {
            return targetUnitId.equals(unitId);
        }
        return matchesCriteria(prefixes, faculty, level, code, unitFaculty, unitLevel);
    }

    /** Every non-empty criterion must hold. No criteria at all matches everything. */
    public static boolean matchesCriteria(List<String> prefixes, Faculty ruleFaculty, UnitLevel ruleLevel,
                                          String code, Faculty unitFaculty, UnitLevel unitLevel) {
        if (prefixes != null && !prefixes.isEmpty()) {
            String upper = code == null ? "" : code.toUpperCase(Locale.ROOT);
            boolean any = false;
            for (String p : prefixes) {
                if (upper.startsWith(p)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        if (ruleFaculty != null && ruleFaculty != unitFaculty) {
            return false;
        }
        return ruleLevel == null || ruleLevel == unitLevel;
    }

    /** Higher wins a tie: a unit-specific row over a three-criterion rule over a site-wide one. */
    public int specificity() {
        if (targetUnitId != null) {
            return 4;
        }
        return (prefixes.isEmpty() ? 0 : 1) + (faculty == null ? 0 : 1) + (level == null ? 0 : 1);
    }

    /** Human-readable reason the row is on a page, shown as a chip next to it. */
    public String scopeLabel() {
        return scopeLabel(targetUnitId != null, prefixes, faculty, level);
    }

    public static String scopeLabel(boolean unitSpecific, List<String> prefixes, Faculty faculty, UnitLevel level) {
        if (unitSpecific) {
            return "This unit";
        }
        boolean hasPrefixes = prefixes != null && !prefixes.isEmpty();
        if (!hasPrefixes && faculty == null && level == null) {
            return "All units";
        }
        if (hasPrefixes) {
            StringBuilder sb = new StringBuilder("All ");
            if (level != null) {
                sb.append(level.getDisplayName().toLowerCase(Locale.ROOT)).append(' ');
            }
            sb.append(joinPrefixes(prefixes)).append(" units");
            if (faculty != null) {
                sb.append(" in ").append(faculty.getDisplayName());
            }
            return sb.toString();
        }
        if (faculty != null && level != null) {
            return level.getDisplayName() + " " + faculty.getDisplayName();
        }
        if (faculty != null) {
            return faculty.getDisplayName();
        }
        return level.getDisplayName() + " units";
    }

    private static String joinPrefixes(List<String> prefixes) {
        int n = prefixes.size();
        if (n == 1) {
            return prefixes.get(0);
        }
        if (n == 2) {
            return prefixes.get(0) + " and " + prefixes.get(1);
        }
        if (n <= 4) {
            return String.join(", ", prefixes.subList(0, n - 1)) + " and " + prefixes.get(n - 1);
        }
        return String.join(", ", prefixes.subList(0, 3)) + " and " + (n - 3) + " more";
    }

    /** Splits and normalises a raw prefix string: trimmed, upper-cased, blanks dropped, duplicates removed. */
    public static List<String> splitPrefixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    /** The stored form of a prefix list, or null when there is no prefix criterion. */
    public static String joinForStorage(List<String> prefixes) {
        return prefixes == null || prefixes.isEmpty() ? null : String.join(",", prefixes);
    }
}
