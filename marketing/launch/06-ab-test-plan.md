# Poster A/B test plan

How one person, by hand, can find out which of the three poster designs works
and trust the answer. Everything here uses what the platform already records:
visits, signups and reviews per referral slug, shown in the admin Campaigns
page.

## What is being tested

| Design | Hook | Slug (QR) | Phase |
|---|---|---|---|
| A, Rant or rave (black) | Semester's done. Say what it was actually like. | `poster-rant` | 1, and 2 if winning |
| B, Be the first (paper) | Most Curtin units have zero reviews. Yours is probably one. | `poster-first` | 1, and 2 if winning |
| C, Results are out (gold) | Results are out. Which units earned it? | `poster-results` | 2 only |

A versus B is the real test in phase 1. C is a seasonal variant that only makes
sense from 2 December; compare it against the phase 1 winner in phase 2, with
the caveat that the audience and the calendar have changed.

## Attribution: one slug per design, not per location

Each design's QR encodes `https://www.curtinhonestly.com/?ref=<slug>`. The
frontend captures the slug on any page, stores it, records one visit per slug
per browser session, and attributes a later signup and that user's reviews to
the slug. So one slug per design gives, for free:

- scans (visits per slug, undercounted by people who type the URL instead),
- signups attributed to the design,
- reviews attributed to the design.

Per-location slugs (for example `poster-rant-lib`) are possible by editing the
`POSTERS` dict in `03-posters/build.py` and reprinting. Do not do this for the
first run. It multiplies the number of slugs to create and halves the count per
cell, and location is controlled by placement instead.

## Controlling for location: pairs first, rotation second

Posters fail A/B tests mostly because the better spot wins, not the better
design. Two methods, in order of preference.

**Method 1, paired placement (preferred).** Put A and B side by side on the
same board, same height, same size. Every board carries both. Halfway through
the run, swap left and right on every board. Location is identical for both
designs by construction, and the swap removes the left-right bias. This needs
boards with room for two A3 sheets or two A4 flyers; most Curtin noticeboards
have it.

**Method 2, rotation (when only one poster fits per spot).** Split the spots
into two groups of equal quality as best you can judge (library versus
engineering building is not equal; mix each group). Week 1: A in group 1, B in
group 2. Week 2: swap. Compare each design's total across both weeks. With
three designs and three groups, rotate as a Latin square over three weeks:
(A,B,C), (B,C,A), (C,A,B).

Either way, keep a log. A spreadsheet with one row per poster: spot, design,
date up, date checked, still up (yes or no), date taken down. Posters get torn
down, and a design that lost half its sheets in week one did not lose fairly.

## How long, and how many scans

Campus posters draw few scans. A reasonable planning assumption is 0.5 to 3
scans per poster per week. With 8 sheets of each design up for two weeks, expect
roughly 10 to 50 scans per design in total.

Decision rule, simple enough to do on paper. If the two designs were equally
good, the difference in their scan counts would wander by about the square root
of the total. Call a winner only when:

```
|scans A - scans B|  >=  2 x sqrt(scans A + scans B)
```

That is about a 95 percent threshold for a two-sided check. Worked numbers:

| Total scans | Winner needs at least | Example that counts |
|---|---|---|
| 30 | a gap of 11 | 21 vs 9 |
| 50 | a gap of 15 | 33 vs 17 |
| 100 | a gap of 20 | 60 vs 40 |
| 200 | a gap of 29 | 115 vs 85 |

Two consequences. First, with the scan volumes a campus run produces, only a
large difference (roughly 2 to 1) can be detected. That is fine: a design that
is not clearly better is not worth the reprint. Second, do not call it early.
Run the full two weeks, including the swap, then look once. Peeking daily and
stopping on the first lead is how coin flips become findings.

If after two weeks the gap is under the threshold, the designs are tied on
scans. Decide on the next metric down.

## What to measure, in priority order

1. **Reviews attributed per design.** The outcome that matters. Also the
   smallest number, so it will rarely be decisive on its own.
2. **Signups per design.** The step before reviews, usually 3 to 5 times more
   common than reviews.
3. **Scans per design** (visits per slug). The leading indicator and the only
   one likely to reach the threshold.
4. **Conversion**: signups divided by scans, reviews divided by signups. If one
   design brings more scans but fewer reviews per scan, it is attracting the
   curious rather than the motivated. Prefer the design with more reviews per
   poster-week unless the difference is tiny.
5. **Exposure**: poster-days up, from the log. Normalise everything to "per
   poster-week" before comparing, because torn-down sheets lower a design's
   totals through no fault of its own.

## How to decide

At the end of phase 1 (Tue 1 Dec):

- Reviews clearly favour one design (gap at or above the rule above)? That
  design is the winner. Reprint it for phase 2 and pair it with C.
- Reviews tied, scans clearly favour one? Pick the scan winner. Scans are the
  cheaper signal and the designs' conversion rates are probably similar.
- Everything tied? Keep both up in phase 2 (they are already printed) and
  pair C with whichever had the higher reviews-per-scan, even if not
  significant. Decide the Semester 1 2027 design on the combined data from both
  semesters.
- One design drew complaints, was removed by building staff more often, or
  read badly in a dim corridor? That is also data. Note it in the log.

At the end of phase 2 (Wed 16 Dec), repeat the comparison between C and the
phase 1 winner, separately for scans and reviews, and write three lines in the
results post: which design won, by how much, and what gets printed next time.

## Practical checklist

- [ ] Create `poster-rant`, `poster-first` and `poster-results` as referral links in the admin UI.
- [ ] Print one proof of each, scan it with two different phones, confirm the visit appears against the right slug.
- [ ] Decide method (pairs or rotation) per spot and write the placement plan before printing quantities.
- [ ] Check Guild and Curtin Properties poster rules; get written permission where boards are managed.
- [ ] Put posters up the same day for both designs. Photograph each spot.
- [ ] Check spots twice a week. Log removals. Replace from spares the same day so exposure stays even.
- [ ] Swap sides (pairs) or groups (rotation) at the midpoint, Mon 23 Nov for phase 1.
- [ ] Look at the numbers once, on Tue 1 Dec. Apply the rule. Write down the decision.
- [ ] Repeat for phase 2, decision on Wed 16 Dec.

## Social images

The 9:16 images carry their own slugs (`social-rant`, `social-first`,
`social-results`) so a Discord or Instagram post that uses one is attributable
separately from the printed sheets. Do not read a social A/B as clean: each
image goes to a different society or on a different day, so the audience is
confounded with the design. Treat social slugs as channel measurement, not as a
design test.
