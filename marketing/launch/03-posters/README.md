# Posters and social variants

Three poster concepts, each with its own QR code and `?ref=` slug so scans
attribute per design, plus a 9:16 variant of each for Instagram stories and
Discord. All assets carry the `https://www.curtinhonestly.com` URL and the line
"Independent student platform. Not affiliated with Curtin University."

| Design | Source | Slug | Concept |
|---|---|---|---|
| A | `poster-rant.html`, `social-rant.html` | `poster-rant`, `social-rant` | Rant or rave. Black, white and gold. "Semester's done. Say what it was actually like." with fill-in-the-blank prompts instead of testimonials |
| B | `poster-first.html`, `social-first.html` | `poster-first`, `social-first` | Be the first. Off-white. "Most Curtin units have zero reviews. Yours is probably one." with five faculty rows of empty stars, labelled illustrative |
| C | `poster-results.html`, `social-results.html` | `poster-results`, `social-results` | Results are out. Gold. "Which units earned it?" with a grade ladder; for phase 2 from 2 December |

## What is in `export/`

| File pattern | Use |
|---|---|
| `<name>-A3.pdf` | Print master, vector, 297 x 420 mm, no bleed, fonts embedded. Give this to the printer |
| `<name>-A4.pdf` | Flyer master, 210 x 297 mm |
| `<name>-A3.png`, `<name>-A4.png` | 300 dpi rasters (3508 x 4961 and 2480 x 3508) for print shops that want images |
| `<name>-preview.png` | Small preview for checking on screen |
| `social-<name>-9x16.png` | 1080 x 1920 for stories and Discord. Crop to 4:5 for the Instagram feed |

`qr/` holds each QR as SVG (used by the posters) and PNG (for pasting
elsewhere), and `qr/links.md` lists slug to URL.

## Rebuilding

Requires Python 3 with `qrcode` and `Pillow`, and Chrome or Edge.

```
cd marketing/launch/03-posters
python build.py                 # full build, prize strip on
python build.py --no-prize      # hide the prize strip (nothing else moves)
python build.py --base https://www.curtinhonestly.com/sem2   # retarget the QR codes
```

The QR codes encode `<base>?ref=<slug>`. Referral capture is site-wide, so the
homepage works today; once the `/sem2` campaign page exists, rebuild with
`--base` so scans land on the prize and rules.

## Before printing

1. Create every slug as a referral link in the admin UI. Visits to an unknown
   slug are not recorded.
2. Print one A4 proof of each design and scan each QR with two phones. Confirm
   the visit appears against the right slug in the admin Campaigns page.
3. Check the footer line is legible at A4. It is set at 19 px on the A3 sheet,
   which prints at about 13.5 px equivalent on A4; fine on paper, tight on a
   poor photocopy.
4. Ask for 150 to 200 gsm uncoated stock for posters. Gold is a flat fill
   (`#C09711`) and prints without special handling.

## Design rules applied

- Colours and fonts from `design/colours/colours.md` and `design/fonts/fonts.md`:
  gold `#C09711` as a fill only, never as text on white; `#8A6D0C` for gold
  text on light surfaces; Roboto Condensed for display, Rubik for body, Nimbus
  Sans for small print.
- Logo lockup copied from `design/branding/Logo/final.png` into `assets/`. The
  official Curtin logo and crest are not used anywhere.
- No invented reviews. Design A uses prompts. Design B's rows are labelled
  illustrative on the sheet.
- Each sheet carries a small variant letter (A, B, C) bottom right for the
  placement log.
- No em dashes.

## Editing

The HTML sources are plain files; edit text in place and rebuild. Font sizes
are tuned so the display lines fit a 995 px column at A3 (Roboto Condensed
Extra Bold capitals run about 0.55 em per character). The sheet is a flex
column with the CTA block pinned by `margin-top: auto`, so adding content
pushes the CTA down; if the footer overlaps the CTA in the preview, trim
something above it.
