# Giveaway plan

The platform already has the mechanic: a campaign with a promo code, referral
links, per-review draw entries, a minimum review length, an entry cap, a
verified-student requirement and a close date, all editable in the admin UI.
This document picks the prize, the rules and the budget, and flags the legal
check that has to happen before any of it is announced.

## Recommended giveaway

One campaign, one draw, two phases of promotion.

| Setting (admin field) | Value | Why |
|---|---|---|
| Name | Semester 2 2026 review drive | |
| Code (`code`) | `SEM2-2026` | Typed at registration from a flyer or post; optional, the referral links do the same job |
| Slug (`slug`) | `sem2-2026` | |
| Prize description | see options below | Shown on the Account page |
| Starts (`startsAt`) | Sat 14 Nov 2026 00:00 AWST | Day after exams end |
| Ends (`endsAt`) | Sat 12 Dec 2026 23:59 AWST | Ten days after results, gives phase 2 a deadline |
| Min review length (`minReviewLength`) | 120 characters | Default is 50. 120 is about two sentences, enough to be useful and hard to farm |
| Max entries per user (`maxEntriesPerUser`) | 5 | Default. Five units is a full semester load |
| Require verified student | on | The prize is for Curtin students; verification is the only check available |
| Required review count | 1 | One qualifying review is one entry |
| Likes thresholds | 0 | Likes are gameable; leave off |
| Max redemptions | blank | No cap on participants |

One review per user per unit is enforced by the database constraint, so entries
cannot be farmed on one unit.

### Prize options

Gift cards, because students can use them, they post easily, and no physical
prize needs collecting. Avoid alcohol, tobacco and anything age-restricted as
prizes; they trigger extra conditions in most Australian jurisdictions.

| Option | Prizes | Pool (AUD) | Notes |
|---|---|---|---|
| **A, recommended** | 1 x $150 gift card, 3 x $50 gift cards | 300 | Four winners feels fair for a draw; a $150 headline is enough to read |
| B, lean | 2 x $100 gift cards | 200 | Smaller headline, fewer winners |
| C, bigger headline | 1 x $250 gift card, 2 x $50 | 350 | Only if a society co-hosts and the pool is shared |

Card type: a flexible digital card (Prezzee or a Coles Group and Myer card)
beats a single retailer. Digital delivery by email keeps fulfilment to ten
minutes.

Co-hosting: if ComSSA or CEC wants its name on the draw, the cleanest form is
"CurtinHonestly and ComSSA Semester 2 review draw" with the society adding one
prize from its own budget and posting the draw to its members. The terms then
name both promoters.

### Entry rules, in the words that go on the campaign page

- You need an account verified with an @student.curtin.edu.au email.
- Write a review of a Curtin unit you have taken, between 14 November and
  12 December 2026, at least 120 characters long, that follows the site rules.
- Each qualifying review is one entry. One review per unit. Up to five entries.
- Reviews removed under moderation, duplicates and low-effort reviews earn
  nothing, and entries already recorded for them are withdrawn. Deleting a
  review removes its entry.
- Draw on Monday 14 December. Winners are emailed and have seven days to claim.
- Devon and his household cannot enter.

The full terms are Appendix A of `02-launch-runbook.md`.

### Draw method

Export the valid entries (campaign entries table, one row per entry, each
tied to a review id). Remove any whose review has since been removed. Number
them. Pick winners with a recorded random process: for example a Python one-liner
seeded with a value written down before the draw, screen-recorded, with the
recording kept until prizes are claimed. First draw wins the first prize, one
prize per person, skip and redraw if a person is drawn twice. Email winners
from contact@curtinhonestly.com within two days. Redraw unclaimed prizes after
seven days.

## Estimated costs

All figures AUD, estimates to be confirmed at the counter. Print prices are
typical Officeworks walk-in rates in Perth in 2026 for colour on standard
stock; online print shops are often cheaper per unit for runs over 100 but add
freight and days.

### Prizes

| Item | Low | High |
|---|---|---|
| Prize pool (option B to option C) | 200 | 350 |
| Card fees (digital delivery, usually nil) | 0 | 10 |
| **Prizes subtotal** | **200** | **360** |

### Printing and placement

A realistic run for one person placing posters by hand across the Bentley
campus and nearby student housing, with spares for replacements.

| Item | Quantity | Unit low | Unit high | Low | High |
|---|---|---|---|---|---|
| A3 colour posters, 150 to 200 gsm, designs A and B for phase 1 (8 of each plus 4 spares each) | 24 | 2.50 | 3.50 | 60 | 84 |
| A3 colour posters, design C plus the phase 1 winner for phase 2 (6 of each) | 12 | 2.50 | 3.50 | 30 | 42 |
| A4 colour flyers, 100 gsm, three designs (50 each) for library tables, housing boards, society handouts | 150 | 0.90 | 1.20 | 135 | 180 |
| Laminating 6 A3 sheets for outdoor or high-touch boards | 6 | 3.00 | 5.00 | 18 | 30 |
| Blu Tack, tape, pins | 1 | 12 | 20 | 12 | 20 |
| Proof prints (A4, three designs, two rounds) | 6 | 1.00 | 1.20 | 6 | 8 |
| **Printing subtotal** | | | | **261** | **364** |

Cheaper paths worth asking about: Guild-affiliated clubs sometimes get free or
discounted printing through the Guild, so a co-hosting society may be able to
print the posters; and a student print quota may cover the A4 proofs and some
flyers.

### Totals

| Scenario | Prizes | Printing | Total |
|---|---|---|---|
| Lean (option B, half the print run) | 200 | 130 to 180 | **330 to 380** |
| Recommended (option A, full run) | 300 | 261 to 364 | **560 to 660** |
| Bigger headline (option C, full run) | 350 | 261 to 364 | **610 to 710** |

Planning range: **AUD 330 to 660**, with the recommended shape near 600.

## Legal check before announcing a prize

This is a check to perform, not legal advice.

In Australia, a prize draw tied to doing something (here, writing a review) is
a trade promotion, and each state regulates them differently. The campaign is
run from Western Australia and aimed at students in Western Australia, so the
Western Australian rules apply first. Before any prize is announced, on a
poster, a post or the site:

1. **Read the current Western Australian conditions for trade promotion
   lotteries** on the Department of Local Government, Sport and Cultural
   Industries (DLGSC) site, Racing, Gaming and Liquor section. Confirm whether a
   permit or notification is required at this prize value (historically
   Western Australia has not required a permit for trade promotions but has
   imposed conditions on how they are run), and what the conditions require the
   published terms to contain (typically: promoter identity, entry period,
   draw date, time and place, prize descriptions and values, how winners are
   notified and published, and free entry).
2. **Confirm the age rule.** Some first-year students are 17. Check whether
   entrants must be 18 or over and, if so, say so in the terms and ask the
   winner to confirm age before delivery.
3. **Confirm that "write a review" counts as a free entry** and that no
   purchase or payment is involved at any step (it is not).
4. **Confirm the prize types are permitted** without extra conditions. Gift
   cards are the safe choice. Do not offer alcohol, tobacco, firearms, cosmetic
   procedures or travel.
5. **Confirm whether winners must be published** and how (handle versus full
   name), and align the terms and the Privacy Policy with it.
6. **Australian Consumer Law.** The promotion must not mislead: the prize, the
   odds (not fixed, depends on entries) and the conditions must be stated as
   they are. The terms in Appendix A are written to be literal.
7. **If the draw is also promoted to students outside Western Australia**
   (interstate or online students), check whether New South Wales, the ACT or
   South Australia thresholds are engaged. At a $300 pool they are below every
   state's permit threshold as those thresholds have stood, but confirm.
8. **Curtin and Guild policy.** Check whether promoting a third-party prize
   draw on campus boards or in affiliated-club channels needs approval, and
   whether club constitutions limit co-hosting.

Record the outcome of each item here with the date and the source page, then
fill the bracketed values in Appendix A of the runbook and set the prize strip
on the posters (`python build.py`, or `--no-prize` to print without it).

| Check | Date | Outcome and source |
|---|---|---|
| 1. Permit or notification | | |
| 2. Age rule | | |
| 3. Free entry | | |
| 4. Prize types | | |
| 5. Winner publication | | |
| 6. ACL wording | | |
| 7. Other states | | |
| 8. Curtin and Guild | | |
