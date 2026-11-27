# Poster A/B test: method for one person with a stapler

How to find out which poster design works and trust the answer, using only
what the platform already records (visits, signups and reviews per referral
slug, in the admin Campaigns page). Plan section 10 says judge every link on
reviews, not visits. This file is how to get a fair comparison between designs.

## What is being compared

| Design | Slug (QR) | Job (plan 4.2) | When it is up |
| --- | --- | --- | --- |
| Poster 02, "0 reviews." | `poster-a3-zero` | Supply | Mon 9 Nov to Tue 1 Dec |
| Poster 03, "Not affiliated. That is the point." | `poster-a3-someone` | Objection | Mon 9 Nov to Tue 1 Dec |
| Poster 04, "Results are out." | `poster-a3-results` | Supply, phase 2 | Wed 2 Dec to Wed 16 Dec |
| Poster 01, "Same unit. Two descriptions." | `poster-a3-enrol` | Demand | February 2027, not this test |

Phase 1 is a real test: 02 versus 03, two designs with different jobs on the
same walls at the same time. Phase 2 compares 04 against the phase 1 winner,
with the caveat that the audience and the week have changed.

## Attribution: one slug per design, not per location

Each QR encodes `https://www.curtinhonestly.com/?ref=<slug>`. The app captures
the slug on any page, keeps it, records one visit per slug per browser session,
and attributes a later signup and that account's reviews to the slug. One slug
per design therefore gives scans, signups and reviews per design for free.

Per-location slugs are possible (generate `qr-poster-a3-zero-lib.png` with the
snippet in README.md and swap the image in a copy of the mockup) but do not do
it for the first run: it multiplies the slugs to create, halves the count per
cell, and location is controlled by placement instead.

## Controlling for location

Posters fail A/B tests because the better spot wins, not the better design. Two
methods, in order of preference.

**Pairs (preferred).** Put 02 and 03 side by side on the same board, same height,
same size. Every board carries both. At the midpoint (Mon 23 Nov) swap left and
right on every board. Location is identical for both by construction and the
swap removes the left-right bias. Most Curtin noticeboards take two A3 sheets.

**Rotation (when only one sheet fits).** Split the spots into two groups of
roughly equal quality, mixing building types in each. Week 1: 02 in group one,
03 in group two. Week 2: swap. Compare totals across both weeks.

Keep a log either way: one row per sheet with spot, design, date up, each check
date, still up or not, date down. Posters get removed, and a design that lost
half its sheets did not lose fairly.

## How long, and how many scans

Campus posters draw few scans; plan for 0.5 to 3 scans per sheet per week. Ten
sheets per design for three weeks gives roughly 15 to 90 scans per design.

Decision rule, on paper. If two designs were equally good, the gap between
their scan counts would wander by about the square root of the total. Call a
winner only when:

```
|scans 02 - scans 03|  >=  2 x sqrt(scans 02 + scans 03)
```

| Total scans | Gap needed | Example that counts |
| --- | --- | --- |
| 30 | 11 | 21 vs 9 |
| 50 | 15 | 33 vs 17 |
| 100 | 20 | 60 vs 40 |
| 200 | 29 | 115 vs 85 |

Two consequences. At campus volumes only a large difference (about 2 to 1) can
be detected, which is fine: a design that is not clearly better is not worth
the reprint. And do not call it early. Run the full window including the swap,
then look once. Peeking daily and stopping on the first lead is how coin flips
become findings.

## What to measure, in order

1. Reviews attributed per design. The outcome, and the smallest number.
2. Signups per design.
3. Scans per design (visits per slug). The leading indicator and the only one
   likely to clear the threshold.
4. Conversion: signups per scan, reviews per signup. A design with more scans
   and fewer reviews per scan is attracting the curious, not the motivated.
5. Exposure: sheet-days up, from the log. Normalise to per sheet-week before
   comparing.

## How to decide

On Tue 1 Dec:

- Reviews clearly favour one design (rule above): that is the winner. Reprint it
  for phase 2 and pair it with 04.
- Reviews tied, scans clearly favour one: pick the scan winner.
- Everything tied: keep both up (they are printed), pair 04 with whichever had
  the higher reviews per scan, and decide the Semester 1 2027 print on the data
  from both semesters.
- A design drew complaints, was removed by building staff more often, or read
  badly in a dim corridor: that is also data. Log it.

On Wed 16 Dec, repeat for 04 against the phase 1 winner, separately for scans
and reviews, and put three lines in the results post: which design won, by how
much, what gets printed next time.

## Checklist

- [ ] Create `poster-a3-zero`, `poster-a3-someone`, `poster-a3-results` as tracking-only referral links in the admin (plan section 10 and 13).
- [ ] Print one A4 proof of each, scan with two phones, confirm the visit lands on the right slug.
- [ ] Write the placement plan (pairs or rotation) before choosing print quantities.
- [ ] Check Guild and Curtin Properties poster rules; print five first (plan 4.4), then the rest.
- [ ] Put both designs up the same day. Photograph each spot.
- [ ] Check twice a week. Log removals. Replace the same day from spares.
- [ ] Swap sides or groups on Mon 23 Nov.
- [ ] Read the numbers once, on Tue 1 Dec. Apply the rule. Write the decision down.
- [ ] Repeat for phase 2; decide on Wed 16 Dec.

## Stories and the slide

The 9:16 stories carry their own slugs (`story-zero`, `story-someone`,
`story-results`, `story-rant`) and the slide carries `slide-lecture` or a
per-unit slug. These measure channels, not designs: each image goes to a
different society or on a different day, so audience is confounded with design.
Do not read them as an A/B.
