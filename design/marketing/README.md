# design/marketing

Launch and marketing material for CurtinHonestly. Written 13 August 2026.

**Start here: [launch-plan.md](launch-plan.md).** Everything else in this folder is
referenced from it.

```
design/marketing
├── launch-plan.md          The plan. Posters, giveaways, Reddit, video, social,
│                           timing, campus tactics, referral tracking, FAQ, budget.
├── README.md               This file.
│
├── mockups/                Editable source. True output size, real brand tokens.
│   ├── index.html          Gallery of all four pieces. Open this first.
│   ├── brand.css           Shared print brand sheet. Mirrors frontend/src/theme.css.
│   ├── poster-01-two-descriptions.html   A3, demand side
│   ├── poster-02-zero-reviews.html       A3, supply side
│   ├── poster-03-not-affiliated.html     A3, objection handling
│   └── slide-lecture-16x9.html           1920 x 1080 lecture-theatre slide
│
├── exports/                Generated output. Regenerate rather than hand-edit.
│   ├── *.pdf               Print-ready A3 at exactly 297 x 420 mm, no margins
│   └── *.png               Screen versions, posters at 2x, slide at 1920 x 1080
│
└── assets/
    ├── shield-gold.png     Gold shield, transparent background, for light surfaces
    ├── wordmark-on-black.png
    ├── logo.png            Copy of the site logo. Has a baked-in black slab.
    │                       Never place it on the off-white background.
    ├── qr-*.png            Real scannable QR codes, one per tracked referral slug
    └── fonts/              Roboto Condensed and Rubik, so the mockups open standalone
```

## Viewing the mockups

The fonts are loaded over HTTP, so serve the folder rather than opening the files
directly:

```bash
python3 -m http.server 4174 -d design/marketing
```

Then open <http://localhost:4174/mockups/index.html>. There is also a `marketing`
entry in `.claude/launch.json` that does the same thing.

## Regenerating the exports

Append `?clean` to any mockup URL to strip the yellow spec note before exporting.

```bash
chrome --headless=new --no-pdf-header-footer --virtual-time-budget=5000 \
  --print-to-pdf=design/marketing/exports/poster-01-two-descriptions.pdf \
  "http://localhost:4174/mockups/poster-01-two-descriptions.html?clean"
```

For the PNGs, swap `--print-to-pdf` for `--screenshot` and add
`--window-size=1123,1588 --force-device-scale-factor=2` for A3 posters, or
`--window-size=1920,1080` for the slide.

## Regenerating the QR codes

Each code encodes a tracked referral slug from section 10 of the plan. They are real
codes, not placeholder art, so they scan and they attribute. Always encode the
`https://www.curtinhonestly.com` host, per section 10, so tracking does not depend on
the apex redirect.

```bash
pip install "qrcode[pil]"
```

```python
import qrcode
from qrcode.constants import ERROR_CORRECT_H

q = qrcode.QRCode(error_correction=ERROR_CORRECT_H, box_size=20, border=2)
q.add_data('https://www.curtinhonestly.com/?ref=poster-a3-enrol')
q.make(fit=True)
q.make_image(fill_color='#1F1F1F', back_color='#FAFAFA').save('qr-poster-a3-enrol.png')
```

Create the matching tracking-only referral link in the admin (Campaigns, "Create
referral link") using the same slug **before** printing anything, or the scans arrive
with nowhere to land.

## House rules for anything added here

1. No em dashes. Devon's preference, applied to every file in this folder.
2. Gold `#C09711` is a fill. Gold text on a light surface is `#8A6D0C`, always.
3. Every printed piece carries "Independent student platform. Not affiliated with
   Curtin University."
4. Never invent statistics about a real named unit. Either use live numbers with the
   review count shown, or leave the slot blank on purpose.
