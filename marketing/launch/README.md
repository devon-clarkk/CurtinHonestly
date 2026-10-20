# CurtinHonestly launch package, Semester 2 2026

Everything needed to run the November and December "rant or rave" campaign:
the plan, the runbook, print-ready posters, the copy, the giveaway and the
poster A/B test. Nothing here deploys. Devon prints, posts and sends.

| File | What it is |
|---|---|
| `01-executive-summary.md` | Goal, angle, the two phases keyed to the Curtin calendar, channels in order, success metrics |
| `02-launch-runbook.md` | Step-by-step, dated. Pre-launch blockers, campaign setup in the admin UI, the public campaign page and campaign terms (draft text included), day-by-day calendar, moderation and measurement, templates |
| `03-posters/` | Three poster concepts (A Rant or rave, B Be the first, C Results are out) as HTML sources, with `export/` holding A3 and A4 PDFs and 300 dpi PNGs, three 9:16 social variants, unique QR codes and `?ref=` slugs per design, and `build.py` to regenerate |
| `04-copy-pack.md` | LinkedIn, Instagram, Discord, r/Curtin, society outreach email, press note, share captions, site banner lines |
| `05-giveaway-plan.md` | Prize options, entry rules mapped to the campaign mechanic, itemised cost estimate, the Western Australian trade-promotion check |
| `06-ab-test-plan.md` | How to A/B physical posters by hand: attribution per design, paired placement and rotation, duration, how many scans, what to measure, how to decide |

## Fixed facts every file relies on

- Site: `https://www.curtinhonestly.com` (always the `https` `www` form; the `http` apex currently redirect-loops).
- Coverage: 1,761 units across five faculties. Reviews on site: 47 on 32 units at the 1 September audit.
- Calendar (Curtin, Semester 2 2026): exams Mon 2 Nov to Fri 13 Nov, teaching ends Fri 13 Nov, results Wed 2 Dec.
- Phase 1, "review it while it's fresh": Sat 14 Nov to Tue 1 Dec. Phase 2, "results are out": Wed 2 Dec to Wed 16 Dec. Prize draw closes Sat 12 Dec.
- Mandatory line on every printed or visual asset: "Independent student platform. Not affiliated with Curtin University."
- Never use the official Curtin logo or crest. The file at the CURTIN HONESTLY folder root named `2236.png` and anything under `design/curtin-branding-inspiration/` are off-limits.
- No invented testimonials. Sample content is labelled illustrative. No em dashes.

## Referral slugs used across the package

| Slug | Where it goes |
|---|---|
| `poster-rant`, `poster-first`, `poster-results` | Printed posters and flyers A, B, C (QR codes) |
| `social-rant`, `social-first`, `social-results` | 9:16 images A, B, C (QR codes in the image) |
| `comssa`, `cec`, `cdsa`, `robotics` | Society emails, Discord posts and any club-page link |
| `linkedin`, `instagram`, `rcurtin`, `grok`, `email` | Devon's own channels, the subreddit, student media, any user mailing |

Create every slug as a referral link in the admin UI (Campaigns, Create referral link) before anything is printed or posted. Visits to a slug that does not exist are not recorded.
