"""Subset the bundled webfonts to the characters this site renders.

The four faces shipped as full Unicode builds: 372 kB of woff2 against a page
that gzips to 10 kB, which made fonts the site's dominant payload by a wide
margin. Almost all of it is scripts an English-language Curtin site never draws.

This keeps the same typefaces and the same variable weight axes. It only drops
glyphs, so nothing renders differently for the characters that remain, and a
character outside the range falls back through the stack in theme.css rather
than failing: every face there is followed by real system fonts, and
font-display: swap means text is painted in the fallback from the first frame
regardless.

Run it after replacing or updating a font file, from the frontend directory:

    python -m pip install fonttools brotli
    python scripts/subset-fonts.py

It rewrites the .woff2 files in place from the .ttf/.otf source next to them,
so it is repeatable and the sources stay the record of what the fonts are.
Pass --check to report sizes and coverage without writing anything.
"""

import argparse
import os
import sys

try:
    from fontTools import subset
    from fontTools.ttLib import TTFont
except ImportError:  # pragma: no cover - tooling guard
    sys.exit("fonttools is required: python -m pip install fonttools brotli")

FONT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "public", "assets", "fonts")

# The source each woff2 is built from. Keeping the originals means the subset is
# always regenerated from the full font rather than from a previous subset.
FACES = [
    ("Rubik-VariableFont_wght.ttf", "Rubik-VariableFont_wght.woff2"),
    ("RobotoCondensed-VariableFont_wght.ttf", "RobotoCondensed-VariableFont_wght.woff2"),
    ("NimbusSanL-Reg.otf", "NimbusSanL-Reg.woff2"),
    ("NimbusSanL-Bol.otf", "NimbusSanL-Bol.woff2"),
]

# What the site draws, and a generous margin around it.
#
# Reviews are free-form student writing, so the Latin blocks run well past
# ASCII to cover accented names. The punctuation and symbol ranges carry the
# characters the UI itself uses: the star ratings (U+2605, U+2606), the
# truncation ellipsis, bullets, and the middle dot. Emoji are not here on
# purpose; no text face contains them and the platform emoji font draws them.
UNICODES = ",".join(
    [
        "U+0020-007E",  # Basic Latin
        "U+00A0-00FF",  # Latin-1 Supplement, accented names
        "U+0100-017F",  # Latin Extended-A
        "U+0180-024F",  # Latin Extended-B
        "U+02B0-02FF",  # Spacing modifiers
        "U+0300-036F",  # Combining diacritics
        "U+2000-206F",  # General punctuation: ellipsis, bullets, quotes, dashes
        "U+20A0-20BF",  # Currency
        "U+2100-2131",  # Letterlike: numero, degrees
        "U+2190-2199",  # Arrows
        "U+2212",       # Minus
        "U+2600-26FF",  # Misc symbols, includes the rating stars
        "U+FEFF",       # Zero-width no-break space
    ]
)


def options():
    opts = subset.Options()
    opts.flavor = "woff2"
    opts.layout_features = ["*"]
    # Keep the weight axis: theme.css declares `font-weight: 100 900` on the
    # variable faces and the design uses several weights from one file.
    opts.retain_gids = False
    opts.desubroutinize = False
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.recalc_bounds = True
    opts.drop_tables = ["+DSIG"]
    return opts


def describe(path):
    font = TTFont(path)
    points = set()
    for table in font["cmap"].tables:
        points |= set(table.cmap.keys())
    axes = [(a.axisTag, a.minValue, a.maxValue) for a in font["fvar"].axes] if "fvar" in font else []
    font.close()
    return len(points), axes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report only, write nothing")
    args = parser.parse_args()

    before_total = 0
    after_total = 0

    for source_name, target_name in FACES:
        source = os.path.normpath(os.path.join(FONT_DIR, source_name))
        target = os.path.normpath(os.path.join(FONT_DIR, target_name))

        if not os.path.exists(source):
            sys.exit("Missing source font: " + source)

        before = os.path.getsize(target) if os.path.exists(target) else 0
        before_total += before

        if args.check:
            points, axes = describe(target) if before else (0, [])
            print("{:44} {:>8} B  {:>5} codepoints  axes={}".format(target_name, before, points, axes))
            continue

        subset.main([source, "--unicodes=" + UNICODES, "--flavor=woff2", "--output-file=" + target])

        after = os.path.getsize(target)
        after_total += after
        points, axes = describe(target)
        saved = (1 - after / before) * 100 if before else 0
        print(
            "{:44} {:>7} -> {:>6} B  ({:4.1f}% smaller)  {} codepoints  axes={}".format(
                target_name, before, after, saved, points, axes
            )
        )

    if not args.check:
        print(
            "\ntotal {} -> {} B  ({:.1f}% smaller)".format(
                before_total, after_total, (1 - after_total / before_total) * 100
            )
        )


if __name__ == "__main__":
    main()
