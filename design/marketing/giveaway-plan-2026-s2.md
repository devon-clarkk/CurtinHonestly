# Giveaway plan, Semester 2 2026

Builds on `launch-plan.md` section 5, which already chose the mechanics (G1 the
results-day draw, G2 the club partnership, G3 coffee for a review, G5 the no-prize
control) against the real admin fields. This file configures G1 and G2 for the
November and December window, itemises the money, and turns the legal note in
plan 5.2 into a checklist with room for the answers.

## What runs

| | G1: the review draw | G2: the club partnership | G5: the control arm |
| --- | --- | --- | --- |
| What | Each qualifying review is one entry in a draw | The society whose link drives the most reviews gets $150 toward its next event | One channel with no prize mentioned at all |
| Who | Verified `@student.curtin.edu.au` accounts | ComSSA, Curtin Engineers Club, CDSA, Robotics, any other club that asks | The lecture slide and coordinator channel |
| Prize | Five $50 vouchers (plan's recommendation: five winners read as winnable and give five announcements) | $150, one winner | None |
| Window | Sat 14 Nov 00:00 to Sat 12 Dec 23:59 AWST | Same window | Same window |
| Legal surface | Trade promotion (see the check below) | None: no draw, no chance, a prize for a measured result | None |
| Cost | $250 | $150 | $0 |

G3 (50 pre-bought $5 coffees at a results-week table, $250) stays optional. Run
it only if a stall spot exists in the week of 2 December; it is an exchange,
not a lottery, and it is the best way to watch people use the form.

### G1 settings, as the admin fields

| Field | Value | Note |
| --- | --- | --- |
| `name` | Semester 2 2026 review draw | Shown on the Account page |
| `code` | `SEM2-2026` | Optional for students; the referral links do the same job |
| `slug` | `sem2-2026` | |
| `prizeDescription` | Five $50 [voucher type] gift cards, one per winner | Fill the voucher type |
| `startsAt` | 14 Nov 2026 00:00 AWST (13 Nov 16:00 UTC) | Day after exams end |
| `endsAt` | 12 Dec 2026 23:59 AWST (12 Dec 15:59 UTC) | Ten days after results |
| `minReviewLength` | 200 | Plan 5.3: about three sentences. The main quality lever. Keep it. |
| `requiredReviewCount` | 1 | One qualifying review, one entry |
| `maxEntriesPerUser` | 5 | Plan 5.3 |
| `requireVerifiedStudent` | true | Keeps the draw honest and pushes verification |
| `minLikesReceived`, `minLikesGiven` | 0 | G4 territory, not yet |
| `maxRedemptions` | blank | |

One review per account per unit is enforced by `uk_reviews_user_unit`; confirm
the constraint exists in production (plans/campaign-promo-codes-runbook.md)
before the window opens.

### G2 settings

Tracking-only referral links `club-comssa`, `club-cec`, `club-cdsa`,
`club-robotics` (plan naming, section 10). Judged on the reviews column only.
Publish the standings weekly in each society's channel; the competition is most
of the motivation. The prize is paid to the club, not a person, so there is no
draw and no lottery.

### Entry rules in the words that go on the campaign page

- You need an account verified with an @student.curtin.edu.au email.
- Write a review of a Curtin unit you have taken, between 14 November and
  12 December 2026, at least 200 characters long, that follows the site rules.
- Each qualifying review is one entry. One review per unit. Up to five entries.
- Reviews removed under moderation, duplicates and low-effort reviews earn
  nothing, and entries already recorded for them are withdrawn. Deleting a
  review removes its entry.
- Draw on Monday 14 December. Winning entry tokens are published on the
  campaign page; check yours on your Account page. Winners are also emailed
  and have seven days to claim.
- The operator and his household cannot enter.

Full campaign terms are Appendix A of `launch-runbook-2026-s2.md`.

### Draw method (plan 5.2, made concrete)

Export the entries CSV from the admin on 14 December. Remove any entry whose
review has since been removed. Pick five tokens with a recorded random process
(a seeded Python `random.sample` with the seed written down beforehand, screen
recorded, recording kept for 12 months). One prize per person: if a person is
drawn twice, skip and draw again. Publish the five winning tokens on the
campaign page the same day. Anyone can check their own token on their Account
page, nobody's identity is exposed, and the record satisfies the "witnessable
or auditable" condition in a way that fits the brand. Email winners from
contact@curtinhonestly.com; redraw unclaimed prizes after seven days and
publish the new token.

## Estimated costs

AUD, estimates. Print prices are the plan's section 4.4 ranges with Perth
walk-in rates as the upper bound; confirm at the counter. Clubs affiliated with
the Guild sometimes have free or discounted printing, which is worth asking a
co-hosting society about.

### Prizes

| Item | Low | High |
| --- | --- | --- |
| G1, five $50 vouchers | 250 | 250 |
| G2, club partnership prize | 150 | 150 |
| Card or voucher fees (digital delivery) | 0 | 10 |
| **Prizes** | **400** | **410** |

### Print run

| Item | Qty | Unit low | Unit high | Low | High |
| --- | --- | --- | --- | --- | --- |
| A3 colour, posters 02 and 03 for exam fortnight and phase 1 (10 each plus 5 spares each) | 30 | 2.00 | 4.00 | 60 | 120 |
| A3 colour, poster 04 plus the phase 1 winner for phase 2 (6 each) | 12 | 2.00 | 4.00 | 24 | 48 |
| A5 flyer A, 200 in a batch (plan 4.4) | 200 | 0.30 | 0.60 | 60 | 120 |
| Laminating six A3 for exposed boards | 6 | 3.00 | 5.00 | 18 | 30 |
| Blu Tack, tape, pins | | | | 12 | 20 |
| A4 proofs, two rounds | 8 | 1.00 | 1.20 | 8 | 10 |
| **Print** | | | | **182** | **348** |

Stickers (100 vinyl, $50 to $70) are an O-Week item in the plan and are left
out of the November run.

### Totals

| Shape | Prizes | Print | Total |
| --- | --- | --- | --- |
| Lean: G1 only, 15 A3 and 100 A5 | 250 | 70 to 130 | **320 to 380** |
| **Recommended: G1 plus G2, full print run** | 400 to 410 | 182 to 348 | **580 to 760** |
| Recommended plus G3 coffee stall | 650 to 660 | 182 to 348 | **830 to 1,010** |

Plan section 12's first-cycle figure of $895 included stickers and G3; the
recommended shape here drops both and lands at roughly $600 to $750.

## Legal check before any prize is announced

This is a checklist to work through, not legal advice. Plan 5.2 summarises the
Western Australian position as of August 2026: trade promotion lotteries do not
need a permit but must be run under prescribed conditions (free entry, no longer
than 12 months, draw within one month of close, witnessable or auditable, records
kept 12 months). Confirm each line below against the current page on the
Department of Local Government, Sport and Cultural Industries site (Racing,
Gaming and Liquor), and record the date and source.

| # | Check | Date | Outcome and source |
| --- | --- | --- | --- |
| 1 | Permit or notification requirement for a trade promotion lottery at this prize value, and whether the conditions have changed since August 2026 | | |
| 2 | What the published terms must contain (promoter, period, draw date, time and place, prize values, winner notification and publication, free entry) and whether Appendix A of the runbook covers it | | |
| 3 | Minimum age for entrants. Some first-year students are 17. If 18 or over is required, add it to the terms and confirm age before delivery | | |
| 4 | That "write a review" is a free entry and nothing in the flow involves payment | | |
| 5 | Prize types permitted without further conditions. Gift cards are the safe choice; no alcohol, tobacco, firearms, cosmetic procedures or travel | | |
| 6 | Whether winners must be published and in what form. The plan's token method publishes tokens, not names; confirm that satisfies the condition | | |
| 7 | Australian Consumer Law: nothing in the promotion overstates the prize, the odds or the conditions | | |
| 8 | If promoted to students outside Western Australia (online or interstate), whether NSW, ACT or SA thresholds are engaged at a $250 pool | | |
| 9 | Curtin and Guild policy on promoting a third-party draw on campus boards and in affiliated-club channels; whether a club co-hosting G2 needs committee approval | | |
| 10 | G2 is a prize for a measured result paid to a club, not a lottery. Confirm it does not fall under the trade promotion conditions, and that the club can accept it | | |

When every row has an outcome, fill the bracketed values in Appendix A of the
runbook, and export the results-day pieces with or without the prize line
(`python export.py` or `python export.py --noprize`).
