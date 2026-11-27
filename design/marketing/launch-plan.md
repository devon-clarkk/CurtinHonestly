# CurtinHonestly launch and marketing plan

Written August 2026. Covers the run from now to Semester 1, 2027 enrolment.

Everything here is built for one person with a small budget, a real product, and no
marketing team. Nothing in it requires a feature that does not already exist.

**Files in this folder**

| File | What it is |
| --- | --- |
| `launch-plan.md` | This document. |
| `README.md` | Folder map, and how to regenerate the exports and QR codes. |
| `mockups/index.html` | Gallery of all four rendered pieces. Open this first. |
| `mockups/poster-01-two-descriptions.html` | A3 poster, demand side. |
| `mockups/poster-02-zero-reviews.html` | A3 poster, supply side. |
| `mockups/poster-03-not-affiliated.html` | A3 poster, objection handling. |
| `mockups/slide-lecture-16x9.html` | 1920 x 1080 lecture-theatre slide. |
| `mockups/brand.css` | Shared print brand sheet. Tokens mirror `frontend/src/theme.css`. |
| `exports/*.pdf` | Print-ready A3, exactly 297 x 420 mm, no margins. Take these to the printer. |
| `exports/*.png` | Screen versions. Posters at 2x, slide at 1920 x 1080. |
| `assets/shield-gold.png` | Gold shield cut clean, transparent background, for off-white surfaces. |
| `assets/qr-*.png` | Real scannable QR codes, one per tracked referral slug. |

Open the mockups with `python3 -m http.server 4174 -d design/marketing` (or the
`marketing` entry in `.claude/launch.json`) so the fonts load. Append `?clean` to any
mockup URL to strip the spec note before exporting.

**Before printing anything:** create the matching tracking-only referral links in the
admin, using the slugs in section 10. The QR codes already point at them, so without
the links the scans arrive with nowhere to land and nothing gets measured.

---

## 1. Where the product actually is, honestly

Marketing that ignores this will fail, so it goes first.

- Production has **133 units and zero reviews** (`plans/versus-pages-seo-spike.md`).
  The catalogue is real, the content is not there yet.
- The catalogue is meant to grow toward the full Curtin handbook, which is several
  thousand units. Right now it is a slice.
- Every feature the plan below leans on is already shipped: student email
  verification and the verified badge, anonymous posting, workload and
  would-take-again and grade fields, compare, unit requests, review reporting,
  a profanity filter, a results-season banner, promo-code campaigns with prize
  draws, and tracking-only referral links.

So this is not a launch problem. It is a **cold-start problem**, and cold start has
one shape: supply first, then demand. A student who lands on an empty unit page
does not come back. A student who lands on a page with eight honest reviews tells
their group chat.

**Everything in this plan is sequenced supply first.** Posters that ask for reviews
go up before posters that promise answers. The one exception is the sample of
demand-side material used to seed word of mouth, because "this exists" has to
travel before "this is useful" can.

### The single number to watch

Not signups. Not visits. **Units with three or more reviews.** Three is roughly the
point where a page stops looking abandoned and starts looking like evidence. Track it
weekly. When it crosses about 40 units, the demand-side material earns its budget.

---

## 2. Positioning and voice

**Tagline:** Authentic anonymous reviews from Curtin students.

**Standing line, everywhere, no exceptions:** Independent student platform. Not
affiliated with Curtin University.

**One-sentence pitch:** Honest student reviews of Curtin units, with ratings,
workload, grades, and would-take-again scores, so you know which units are actually
worth it before you enrol.

That pitch is adapted from Devon's own share copy in
`design/brag-launch-video/share-copy.txt`, which reads "honest student reviews of Curtin
units. Ratings, workload, and would-take-again scores, so you know which units are
actually worth it before you enrol." The only change is adding grades, because the site
shows them. Do not rewrite it further. Consistency across a poster, a Reddit post, and a
TikTok caption is worth more than variety.

### The voice rules

Inferred from what Devon has already written on the site and in the design docs.
Short, plain, second person, practical, dry. It explains rather than sells, and it
tells you the limitation before you find it yourself.

1. **No em dashes.** Hard rule. Use a full stop or a comma.
2. **Second person.** "Which units did you just finish?" not "Students can review units."
3. **Short declaratives.** The launch video's closing line: "Honest reviews. Better
   choices."
4. **Say the limitation out loud.** The site already does this. The privacy page says
   plainly that reviews are still linked to your account in the database and that
   admins can see who wrote one. The 404 page says "Looking for a unit we don't have
   yet? Let us know." Copy that habit. It is the brand.
5. **No hype vocabulary.** No "revolutionise", "empower", "game changer", "seamless",
   "unleash". If a sentence would fit on a startup landing page, cut it.
6. **Never claim to be Curtin, never claim to be against Curtin.** The position is
   independent, not oppositional. "Not affiliated" is a fact stated calmly, not a
   grievance.
7. **Australian English.** Enrol, organise, colour, semester, unit (not "course").

### A note on voice provenance

There is no saved writing-style profile for Devon, so the voice above is inferred from
the repo: the site's UI copy, the footer line, `design/domain/domains.md.md`,
`design/fonts/fonts.md`, `share-copy.txt`, and the Devon-directed notes in
`design/brag-promo-tour/STORYBOARD.md`. Some site copy was written collaboratively, so
this is a good approximation rather than a clean sample.

**Recommendation:** save a writing-style profile (three or four things Devon has
written unaided, plus a short list of words he never uses). Every draft in this
document would get sharper, and the Reddit posts in particular live or die on
sounding like a person rather than a brand.

### The brand, mechanically

Straight from `design/colours/colours.md` and `frontend/src/theme.css`. The print
sheet in `mockups/brand.css` uses these and nothing else.

| Token | Hex | Use |
| --- | --- | --- |
| Background | `#FAFAFA` | Paper. |
| Ink | `#1F1F1F` | Headlines and body. |
| Gold | `#C09711` | **Fill only.** Rules, pills, bars, stars, the shield. |
| Gold ink | `#8A6D0C` | **Any gold text on a light surface.** Never `#C09711`. |
| Black | `#000000` | Foot bands, inverted posters, the site footer. |
| Muted on dark | `#CFCFCF` | Body copy on black. |
| Grey | `#666666` | Secondary text, the handbook column. |
| Hairline | `#E8E8E8` | Every border. 1px. |

Type: **Roboto Condensed** for display and labels, **Rubik** for body. Sharp corners,
no radius. No gradients, no drop shadows, no rounded pill buttons. The site looks like
a handbook on purpose and the print material should too.

**Logo warning, already diagnosed in the storyboard:** `logo.png` has an opaque black
slab baked into the artwork. It must never be placed on the off-white background. For
light surfaces use `assets/shield-gold.png` (cut clean, transparent) next to live text
with "Curtin" in ink and "Honestly" in `#8A6D0C`. That lockup is built in the mockups
and can be lifted straight out.

---

## 3. Timing: the two-beat cycle

The product already knows when reviews get written. `frontend/src/app/utils/results-season.util.ts`
opens a banner from **25 November to 9 December** and **25 June to 9 July**, with the
comment that those two weeks after results are "the only moment most students are
motivated to write one".

The marketing calendar should be that same clock. Do not invent a second one.

**Reviews come in at results. Reviews get read at enrolment.** Two beats, twice a
year. Everything else is preparation for one of them.

Real dates from Curtin's academic calendar:

| Date | What it is | What marketing does |
| --- | --- | --- |
| 20 Jul to 13 Nov 2026 | Semester 2 teaching | Build supply channels. Clubs, coordinators, posters up. |
| 2 to 13 Nov 2026 | Exam period | Poster 02 goes up outside exam venues. Quiet online. |
| **Wed 2 Dec 2026** | **Semester 2 results release** | **The single biggest day of the year. Everything fires.** |
| 25 Nov to 9 Dec 2026 | In-product results banner window | Campaign live, Reddit post, socials daily, lecture slide reruns. |
| Dec 2026 to Jan 2027 | Break | Nothing. Students are not on campus and not thinking about units. |
| ~9 to 12 Feb 2027 | O-Week, Semester 1 2027 | Stall, stickers, demand-side posters, club partnerships renewed. |
| Feb to Mar 2027 | Enrolment and add/drop | Demand-side push. Poster 01, compare feature, SEO pages. |
| ~25 Jun to 9 Jul 2027 | Semester 1 results | Second supply spike. Repeat the December playbook. |

There are about **15 weeks from today to results day**. That is the runway. Use it to
get channels in place, not to burn budget on awareness nobody can act on yet.

### What each phase actually costs in effort

- **Now to late October (build):** club conversations, coordinator emails, poster
  approvals, the Reddit founder post, video shot and cut. Low spend, high legwork.
- **Late October to 13 November (exams):** posters up, lecture slides running, nothing
  online that competes with exam stress.
- **25 November to 9 December (harvest):** the campaign, the daily posting, the giveaway.
  This is where the money goes.
- **February (enrolment):** demand-side. By now the pages have content, so the promise
  is true.

---

## 4. Posters and print

### 4.1 What the poster set has to do

Three different jobs, and one poster cannot do all three:

1. **Supply.** Get a student who has just finished a unit to write about it.
2. **Demand.** Get a student choosing units to look before they enrol.
3. **Objection.** Answer "is this an official Curtin thing?" and "will this be traced
   back to me?" before anyone has to ask.

A wall of three posters that all say the same thing reads as one poster. A wall of
three posters that answer three questions reads as a thing that exists.

### 4.2 Concepts kept

**P1. "Same unit. Two descriptions."** &nbsp; *Rendered:*
`mockups/poster-01-two-descriptions.html`

The flagship. Two columns split by a hairline. Left, in grey, is generic handbook
register: "This unit introduces students to core theoretical frameworks and their
practical application." Right, in a product-true white card with a gold top rule, is a
student review: "Content is genuinely good. The group assignment is 40% and you get
allocated randomly, so start it in week two and do not wait for your group." Under it,
the real tags: Would take again, Workload 8/10, Had an exam, Verified Curtin student.

Payoff line across the bottom: *The handbook tells you what the unit covers. Students
tell you what it costs you.*

It works because it dramatises the entire reason the site exists without attacking
anyone. The handbook is not wrong. It is answering a different question.

*Placement:* enrolment-adjacent walls, the Curtin Connect queue, library entry,
faculty noticeboards, the Guild building.
*Job:* demand. *Ref:* `poster-a3-enrol`.

**P2. "0 reviews."** &nbsp; *Rendered:* `mockups/poster-02-zero-reviews.html`

A giant gold zero, then the site's own empty state rendered exactly as it appears
today, down to the real line "No reviews yet. Be the first to write one!" Payoff: *The
site is new, and it shows. Ninety seconds from you and one of these pages is worth
reading.*

This is the best poster in the set because it is the only one that is completely true
right now, and students can tell. Asking someone to fix a visible problem converts far
better than asking them to join something.

*Placement:* lecture-theatre exits during exam and results weeks, tutorial-room doors,
computer labs, library group-study floors.
*Job:* supply. *Ref:* `poster-a3-zero`.
*Expiry:* retire it once the median unit has reviews. It stops being honest the moment
it stops being true, and printing it after that point would be the one genuinely
off-brand thing in this plan.

**P3. "Not affiliated with Curtin University. That is the point."** &nbsp; *Rendered:*
`mockups/poster-03-not-affiliated.html`

Inverted: black paper, gold rule, white type. Takes the disclaimer that already sits in
the site header and makes it the argument. Then a four-row table that answers the four
real questions: who writes it, who sees your name, what gets removed, who pays for it.
Closing line: *Written by students who gain nothing from telling you it was fine.*

The inversion matters as much as the copy. It gives the set visual range so three
posters on one wall read as a campaign, not a repeat.

*Placement:* high-traffic corridors, Guild noticeboards, anywhere someone is likely to
squint and wonder if this is official.
*Job:* objection handling. *Ref:* `poster-a3-someone`.
*Before printing:* confirm the "a Curtin student, out of pocket" line is still accurate.
If a sponsor or grant ever appears, that row changes or comes off.

**P4. "Someone did this unit before you." (concept, not rendered)**

A3. A single large product-true unit card, but the unit name is a gold-ruled blank and
the stat values are hidden. Rating, Workload, Avg grade, Would take again sit there
labelled and empty. Under it: *Someone did this unit before you. They rated it, timed
the workload, and said whether they would take it again. Scan to see which unit this is.*

Copy:

> **Someone did this unit before you.**
>
> They rated it, timed the workload out of 10, and said whether they would take it
> again. Scan to find out which unit this is.
>
> *Payoff line:* You are about to spend a semester on this. Twenty seconds of reading
> first is not a big ask.

Deliberately withholds the numbers so the QR is the only way to resolve it. Do not
print this one until at least one unit has fifteen or more reviews, then swap the blank
for that real unit and its real numbers with the review count shown. A poster with
invented statistics about a real unit is not something this brand should ever put on a
wall.

*Job:* demand, second wave. *Ref:* `poster-a3-someone-v2`.

**P5. Lecture-theatre slide.** &nbsp; *Rendered:* `mockups/slide-lecture-16x9.html`

1920 x 1080. "Just finished this unit? Say what it was actually like." Three numbered
steps, a large QR on a black panel, the disclaimer in the corner. Built to sit on the
projector for the five minutes before a lecture starts, or as the last slide of a unit
coordinator's final lecture.

This is the highest-yield single asset in the plan. One coordinator agreeing to leave
it up for four minutes puts it in front of 200 students who all just finished the same
unit, at the exact moment the memory is fresh. It costs nothing but an email.

*Ref:* `slide-lecture`, with a per-unit variant (`slide-comp2004`) whenever a
coordinator agrees, so you can see which lectures actually convert.

**P6. A5 QR flyer and sticker family. (concept, not rendered)**

The handout format. Same lockup, one headline, three steps, one large QR taking roughly
the bottom third. Layout is poster P2 cut down: it is the same argument at arm's length
instead of across a corridor. Two versions, because the person taking a flyer at a stall
in December and the person taking one at O-Week are answering different questions.

*Flyer A, supply. For the results-week stall and for clubs to hand out.*

> **You know something the handbook doesn't.**
>
> You just finished a unit. Say what it was actually like: rating, workload out of 10,
> whether you would take it again. About ninety seconds, posted anonymously, your name
> never appears next to it.
>
> Scan. Write one. Someone picking units in February gets to know what you know.

*Flyer B, demand. For O-Week, where nobody can write a review yet.*

> **In a few months you pick electives. Don't pick them blind.**
>
> Ratings, workload scores, and would-take-again percentages, written by students who
> actually did the unit. Free, independent, and not run by the University.
>
> Scan it now so it's in your history when you need it.

*Sticker, one line only:* the shield plus **CurtinHonestly** plus *Read the reviews
before you enrol.* Nothing else fits and nothing else is needed. A sticker with a
paragraph on it is a sticker nobody puts on a laptop. Cheapest sustained impression on
campus, because it rides around on someone else's laptop in every lecture for a year.

*Refs:* `flyer-a5-stall`, `sticker-laptop`. Both QR codes are already generated in
`assets/`.

### 4.3 Cut list, and why

These were drafted and killed. Recorded so they do not get re-proposed in six months.

| Cut | Why |
| --- | --- |
| "This unit had a 40% fail rate. The handbook doesn't mention that." | Unverifiable, and an invented statistic about a real unit is a defamation risk and a credibility risk at the same time. The brand cannot lead with a number it cannot source. |
| Redaction poster, black bars over text, "Anonymous. Actually." | Reads as conspiratorial. The anonymity story here is careful and qualified, and a poster that implies secrecy oversells what the privacy policy actually promises. |
| Tear-off phone-number tabs | Dated, and the QR already does the job with attribution the tabs cannot give. |
| "Your mate's opinion is one data point." | Slightly condescending, and it argues with the student instead of with the handbook. P1 makes the same point without picking a fight. |
| "Enrolment closes Friday." | Deadline pressure that goes stale the day after and requires reprinting for every window. Folded into P1 as a placement decision instead of a printed line. |
| Curtin-gold-heavy layouts imitating official University signage | Would imply affiliation, which is exactly the thing the site spends a header line denying. The palette deliberately echoes without duplicating and the print should hold that line. |
| Meme-format posters | They date in weeks, they undercut the one thing this brand sells, which is being straight with you, and they age badly on a wall for a whole semester. |

### 4.4 Production notes

- **Size:** A3 (297 x 420 mm) for posters, A5 (148 x 210 mm) for flyers. The HTML
  files are set at true print dimensions with `@page { size: A3 portrait; margin: 0 }`,
  so print to PDF straight from the browser at 100% scale.
- **Cost:** roughly $2 to $4 per A3 colour print. Thirty posters is about $90. A5
  flyers run about 30c each in a batch of 200. Vinyl stickers are about 50c to 70c each
  at 100 units.
- **Approval:** assume Curtin requires approval before anything goes on a noticeboard,
  and that unapproved posters get pulled within a day. Check the Guild's poster policy
  and the building-specific rules before printing thirty of anything. Print five, place
  them, see whether they survive a week, then print the rest.
- **Quantity:** start with 5 of each of P1, P2, P3. Fifteen posters, roughly $50.
  Reprint what works.
- **Every poster carries the disclaimer.** No exceptions, including the sticker if it
  physically fits. It is a one-line inoculation against the single most damaging
  misunderstanding the site can have.

---

## 5. Giveaways

### 5.1 The engine already exists, and it is better than most

`admin-frontend/src/app/pages/campaigns/` ships a full prize-draw system. Nothing needs
building. The exact fields:

| Field | What it does |
| --- | --- |
| `slug` | The `?ref=` value. Also the tracking key. |
| `code` | The promo code students type at registration. |
| `name`, `prizeDescription` | Shown to the student on their account page. |
| `startsAt`, `endsAt` | The entry window. |
| `maxRedemptions` | Cap on signups that can join the campaign. |
| `minReviewLength` | Minimum characters for a review to qualify. The main quality lever. |
| `requiredReviewCount` | Reviews needed per draw entry. |
| `maxEntriesPerUser` | Cap on entries, so one person cannot flood the draw. |
| `minLikesReceived` | Review must be marked helpful this many times to qualify. |
| `minLikesGiven` | User must mark this many other reviews helpful. |
| `requireVerifiedStudent` | Restricts entries to verified `@student.curtin.edu.au` accounts. |

Entries are issued as **tokens**, visible to the student on their account page, and
exportable to CSV from the admin. A unique constraint (`uk_reviews_user_unit`) enforces
one review per user per unit, so entries cannot be farmed by spamming a single page.
The terms already carry the anti-farming clause and the rule that deleting a review
removes any entry it earned.

Separately, **tracking-only referral links** exist: no prize, no requirement, a chosen
landing page, and counts of visits, signups, and reviews attributed to the link. Those
are the workhorse and they should be on literally every channel.

### 5.2 The legal position, and why the entry tokens matter

In Western Australia, trade promotion lotteries **do not require a permit**, whether
the game is one of chance or skill. They do have to be run to the prescribed conditions, and the ones
that bite here are:

- Entry must be **free**. Writing a review is a free act, so this is satisfied, but it
  means a giveaway can never be gated behind a payment.
- The lottery must not run longer than **12 months**.
- The draw must happen **within one month** of entries closing.
- **Members of the public must be able to witness the draw** where practicable. Where
  it is not, audit records confirming the draw must be available on request.
- **Records must be kept for 12 months** and produced on request.

This maps onto the product almost exactly. Export the entry CSV, draw a token, and
**publish the winning token publicly**. Anyone can check their own token on their
account page and verify the draw was real, and nobody's identity is exposed. That is
the witnessed draw and the audit record in one move, and it is a genuinely good story
for a site whose entire pitch is honesty.

Confirm the current conditions at wa.gov.au before running the first draw. The rules
above are what applies today, not legal advice.

### 5.3 The shortlist

**G1. The results-day draw.** *Primary. Run it 25 November to 9 December 2026.*

The one giveaway that matters, timed to the two weeks the product itself says are the
only two weeks that matter.

- Prize: one $250 prize, or five $50 prizes. Five smaller prizes read as more winnable
  and produce five winner announcements instead of one.
- Config: `requiredReviewCount: 1`, `minReviewLength: 200`, `maxEntriesPerUser: 5`,
  `requireVerifiedStudent: true`, window 25 Nov to 9 Dec.
- Why 200 characters: it is roughly three sentences. Long enough to be useful, short
  enough not to feel like homework. This is the single most important number in the
  whole campaign, because it decides whether the site fills up with content or with
  "was ok".
- Why verified only: it keeps the giveaway honest, and it pushes people through student
  email verification, which is the thing that makes every review more credible
  afterwards.
- Why five entries max: five reviews from one person is a genuinely good contribution,
  and beyond that quality falls off a cliff.
- Cost: $250 plus about $30 of print. Realistic yield at this scale: 40 to 120 reviews.
- Prize choice: a campus food or bookshop voucher beats cash. It is cheaper to feel
  large, it is obviously student-relevant, and it does not attract people who are only
  there for money.

**G2. Club and society partnership draw.** *Best value in the plan.*

Instead of paying students, pay one club. Each participating club gets a tracking-only
referral link with its own slug (`club-compsoc`, `club-engclub`). The club whose link
drives the most **reviews** (not visits, not signups) by the close date gets a $150
contribution to their next event.

- Cost: $150 total, regardless of how many clubs take part.
- Why it works: clubs already have the distribution, the Discord, the mailing list, and
  the credibility. They are motivated, they self-organise, and their members trust them
  more than they trust a poster.
- Runs entirely on the tracking-only link. No campaign config, no promo code, no draw
  mechanics, no legal surface at all.
- Judged on reviews, so a club cannot win by spamming a link into a group chat.
- Publish the leaderboard weekly. The competition is most of the motivation.

**G3. Coffee for a review.** *In person, O-Week and the results-week stall.*

A table, a laptop or two, and 50 pre-bought $5 campus coffee vouchers. Write a review
at the table, get a coffee.

- Cost: about $250 for 50.
- Not a lottery. It is an immediate exchange, which sidesteps the draw conditions
  entirely and is far simpler to run.
- Highest conversion of anything here, because the whole funnel happens in front of you
  and you get to watch people use the product and hear what confuses them.
- Cap it. When the vouchers are gone, they are gone, and say so on the table sign.

**G4. The quality draw.** *Second half of Semester 1, 2027, once there is traffic.*

Same as G1 but the qualifying bar moves from length to usefulness:
`minLikesReceived: 3`. A review only earns an entry if three other students marked it
helpful.

- Cost: $150.
- Only run this once there are enough readers for likes to accumulate. Before that it
  is an entry nobody can earn, which is worse than no giveaway.
- This is the endgame mechanic. It rewards the review that helped someone, which is the
  only thing the site actually wants.

**G5. No giveaway at all.** *The control arm, and it is a real option.*

Run one results-day channel with no prize, just the ask. Compare its review rate to the
giveaway channels. If the honest ask converts nearly as well, the money is better spent
on print and the giveaway is doing nothing but adding legal surface and attracting
low-effort reviews. Worth knowing rather than assuming, and the tracking links measure
it for free.

### 5.4 Cut

| Cut | Why |
| --- | --- |
| Semester-long points leaderboard | Needs building, and gamifying volume is directly opposed to the product's purpose. `minLikesReceived` already rewards quality without a scoreboard. |
| iPad or AirPods as the headline prize | Attracts entries from people who do not care about the site and will write anything to get one. A $50 campus voucher self-selects for actual students. |
| Referral bounty, cash per person you bring | Turns the referral link into a spam vector, and the link is currently a clean measurement tool. Do not contaminate the instrument. |
| Entry for signing up rather than reviewing | Signups are not the constraint. Reviews are. Never pay for the wrong metric. |

---

## 6. Reddit and forums

### 6.1 The honest route is the primary strategy, and this is not just an ethics note

Undisclosed self-promotion breaks Reddit's site-wide rules on spam and manipulation,
and it breaks most subreddit rules on top of that. That is the ethical objection, and
it stands on its own. But the practical case is stronger, and it is specific:

If a fake "just a student asking" post gets connected to the site, the damage is not a
banned account. Accounts are free. The damage is that **the domain gets filtered**.
Once `curtinhonestly.com` is on a subreddit's spam filter, or worse on Reddit's
site-wide filter, every future post linking it silently disappears, including the
honest ones. A new account does not fix that. It is close to irreversible, and it would
kill the single best organic channel this product has.

The detection vectors are not subtle, and moderators of small university subreddits
watch for exactly these: a new account, low or no karma, a first post that happens to
land on a niche local topic, a domain nobody has seen before appearing the same week
the account was made, several accounts posting in the same subreddit within days, and
writing style that matches across supposedly unrelated users. r/Curtin is small enough
that the moderators will recognise a pattern that would vanish in a large subreddit.

And the specific irony worth stating plainly: this product's entire pitch is that the
reviews are real. Getting caught faking a Reddit post is not an ordinary marketing
misstep for CurtinHonestly. It is a refutation of the product.

Covert drafts are included in section 6.6, clearly labelled, because Devon asked for
them. They are not recommended and they are not the plan.

### 6.2 Step zero: message the moderators first

Do this before any post. Small university subreddits routinely auto-remove self-promo
and ban accounts that skip the ask. A short modmail costs nothing and turns a risk into
a permission.

**Modmail to r/Curtin:**

> Subject: Permission to post about a unit-review site I built
>
> Hi mods,
>
> I'm a Curtin student and I built a site where students can leave anonymous reviews of
> Curtin units. Ratings, workload, would-take-again, that sort of thing. It's
> independent and not affiliated with the University.
>
> Before posting anything I wanted to check what's allowed. I'd like to make one post
> introducing it and asking for feedback, and I'm happy for it to have no link at all
> if you'd prefer, or to only answer in comments.
>
> I'm not trying to sneak an ad in. If the answer is no, that's fine and I won't post.
> If there's a weekly thread or a flair you'd rather I used, tell me and I'll use it.
>
> Thanks,
> Devon

Send the same to any Curtin Discord admins and Facebook group admins. Same principle.

### 6.3 Post A: the founder post

*Post after mod approval. Best timing: results week, or the Monday of a normal teaching
week. Never during exams.*

> **Title:** I built a site for Curtin unit reviews. It has no reviews on it yet, which
> is the honest problem.
>
> I'm a Curtin student. Every time I picked units I did the same thing: read the
> handbook, learn nothing useful from it, then ask around and hope I knew someone who
> had done it.
>
> So I built curtinhonestly.com. You can leave a review of a unit you've done, with a
> rating, a workload score out of 10, whether there was an exam, your grade if you want
> to share it, and whether you'd take it again. Reviews are posted anonymously. If you
> verify with your @student.curtin.edu.au address your review gets a verified badge,
> but your name and email are never shown next to it.
>
> The honest part: there are 133 units in there and almost nothing written about them.
> A review site with no reviews is just a website. So this post is less "check out my
> thing" and more "if you've got two minutes and a unit you have opinions about, that's
> genuinely the whole thing I need".
>
> Things worth saying up front:
>
> - It's not affiliated with Curtin in any way. Nobody there approves what goes on it
>   and nobody there can take a bad review off it.
> - "Anonymous" means other students never see your name or email. It does not mean the
>   database has no idea who you are. Reviews are linked to your account so you can edit
>   and delete them and so I can deal with abuse reports. I'd rather say that than let
>   you find it out later. It's all written out on the privacy page.
> - Reviews get reported and removed for abuse or for breaking the terms. Not for being
>   negative. A one star review with reasons attached is the most useful thing on the
>   site.
> - I'm paying for it myself. There's nothing to buy and no ads.
>
> Happy to take any criticism of it here, including "this is a bad idea because X". If
> there's a feature that would make you actually use it, say so and I'll look at
> building it.

Why this works: it leads with the weakness, the ask is small and specific, every
predictable objection is answered before anyone raises it, and it invites criticism
rather than praise. On a university subreddit that is roughly the only shape of
self-promotion that gets upvoted.

### 6.4 Post B: the genuine question, with disclosure

*Two to three weeks after Post A. This is the honest version of an "organic" post: the
question is real, the answers are useful to everyone reading, and the interest is
declared.*

> **Title:** Which unit did you go into blind and wish someone had warned you about?
>
> Not looking for "this lecturer is bad". More the structural stuff you can only find
> out by doing it. The 40% group assignment with randomly allocated groups. The unit
> where the exam is nothing like the tutorials. The one everyone says is easy that
> quietly eats your whole semester.
>
> I'll start: [Devon's own real example, one specific unit, one specific problem, no
> naming of any individual staff member.]
>
> Disclosure so it's not weird later: I run curtinhonestly.com, which is a unit review
> site, so I obviously have an interest in this question. Answer here though, no need
> to go anywhere.

Why this works: the thread itself is the value. It generates exactly the kind of
specific, structural detail the site wants, it shows the audience what a good review
looks like, and the disclosure sits at the bottom where it belongs, after the post has
already earned its place. Some of the people who answer will go and write it up
properly without being asked.

**Rule:** never offer prize entries for a Reddit comment. That crosses from marketing
into vote and comment manipulation, and it is the fastest route to the domain filter
described above.

### 6.5 Posts C, D, E: the follow-ups

**Post C, results day (2 December 2026), if the mods are comfortable with a second
post:**

> **Title:** Results are out. If you've got five minutes, write down what your units
> were actually like before you forget.
>
> Genuinely the only window where anyone remembers the details. By March you will
> remember "it was fine" and nothing else.
>
> Doesn't have to be my site. Post it here, put it in your course Discord, tell the
> people behind you. The information dying every December is the actual problem.
>
> (I do run curtinhonestly.com, so, declared. There's a prize draw running until the
> 9th if that helps, details on the site.)

Offering the prize draw is fine here because it is disclosed, it is on the site rather
than for the comment, and the post is useful without it.

**Post D, the six-week update:** real numbers, including the bad ones. "Six weeks on:
340 reviews across 71 units, 62 units still empty, here's what I got wrong." Build in
public. Numbers-with-failures posts consistently outperform launch posts on Reddit, and
this one costs nothing but honesty.

**Post E, the AMA-shaped post:** "I built the Curtin unit review site. Ask me anything
about how it handles anonymity, moderation, or why it isn't affiliated with Curtin."
Only run this after the site has actual content and a few weeks of goodwill. It is a
consolidation post, not a launch post.

### 6.6 Optional guerrilla tactics, not recommended

**Read section 6.1 before using anything below.** These are drafted because they were
asked for. Undisclosed promotion breaks Reddit's rules and the subreddit's rules, the
realistic downside is the domain being filtered site-wide, which cannot be undone with
a new account and which also destroys the honest route, and for this product
specifically, being caught faking authenticity is a refutation of the product's core
claim. The expected value is negative. Recommendation: do not use these.

*Covert draft 1, the "seeker" post:*

> **Title:** Is there anywhere that actually tells you what a unit is like before you enrol?
>
> The handbook is useless, RMP is basically dead for Curtin, and asking in here works
> but only if someone happens to have done the exact unit. Has anyone found something
> better? Feels like there should be a proper site for this.

*Covert draft 2, the "found it" reply, posted from a second account:*

> Someone linked me curtinhonestly.com the other week. It's pretty new so there isn't
> much on it yet, but the format is right. Workload score, would take again, and the
> reviews are anonymous.

Why these fail in practice, beyond the rule breach: two accounts, one small subreddit,
one obscure domain, days apart, is the exact pattern moderators look for. If a real
student later posts the same recommendation genuinely, it lands in a thread that has
already been poisoned. And section 6.4 gets you almost the same thread with none of it,
which is the actual argument against them.

### 6.7 Where else

| Channel | Approach |
| --- | --- |
| Curtin Discord servers, course and club | Highest value per message. Ask the admin first, post once, be a member not an advertiser. Own tracking link per server. |
| Curtin Facebook groups, Guild and year cohorts | Same protocol. Slower audience, still worth one honest post around results day. |
| r/perth, r/australianuniversities | Only for a genuinely wider-interest post, such as the build-in-public numbers one. Do not cross-post the ask. |
| Unit-specific group chats | Not yours to post in. Give the lecture slide to someone who is already in them and let them decide. |

---

## 7. Video

Two pieces already exist: the 20 second launch cut (`design/brag-launch-video/`) and
the newer 33 second site tour (`design/brag-promo-tour/`), which follows Devon's
"search, discover, scroll, close" narrative and ends on the tagline. The tour is the
better spine. Everything below builds off it rather than starting again.

The single most useful production decision: **shoot vertical first.** Every concept
below is 9:16 unless stated. The existing 1920 x 1080 pieces stay as the website and
YouTube versions, but the distribution that matters is Reels, TikTok, and Instagram
Stories, and a letterboxed landscape cut in a vertical feed reads as an advert and gets
scrolled.

**V1. "The search." 9 seconds, vertical.** &nbsp; *Hook: "Curtin University reviews" typed
into a search bar, and one result is worth clicking.*

Lift scenes 1 and 2 of the promo tour, recut vertical, hard stop the moment the site
loads. Nine seconds, no explanation, no voiceover, text on screen only. This is the
top-of-funnel asset and it is nearly free because it already exists. The hook works
because it shows the viewer their own behaviour before it shows them the product.

**V2. "Ninety seconds." 45 seconds, vertical, screen recording.** &nbsp; *Hook: "This is
how long it takes to review a unit. I'm not cutting anything out."*

One unbroken screen recording of a real review being written start to finish, on a
phone, with a running timer in the corner. No jump cuts. The timer is the whole idea:
the biggest objection to writing a review is that it will take a while, and an unedited
clip is the only way to disprove that. If it takes longer than ninety seconds, show
that number instead and fix the form.

**V3. Vox pop. 30 to 60 seconds, vertical.** &nbsp; *Hook: "What's the one unit you'd warn
someone about?"*

One question, asked to ten people on campus, cut to the best six answers. No branding
until the last three seconds. Offer to keep faces out of frame, hands and voice only,
for anyone who wants it, and say so on camera when you ask. It costs one afternoon and
produces the most watchable thing on this list, because it is other students talking,
which is exactly the product.

Get verbal consent on camera. Do not use a clip that names an individual staff member
critically.

**V4. "Two descriptions." 20 seconds, vertical.** &nbsp; *Hook: handbook copy types itself
out, then a student review types over the top of it.*

The video of poster P1. Handbook prose in grey, typing at machine speed, then it fades
and the real review types in over it in ink and gold with the tags popping in on the
beat. Ends on the payoff line. Cheap to build, reuses the promo tour's motion language,
and reinforces the poster on campus at the same time.

**V5. Founder piece to camera. 45 seconds, vertical, no music.** &nbsp; *Hook: "I made a
review site for Curtin units and right now it's empty. Here's why I built it anyway."*

Devon, phone camera, one take, no music, no captions beyond burnt-in subtitles. Says
what it is, says it is not affiliated, says the anonymity limitation out loud, asks for
one review. This is the asset that pairs with the Reddit post and it is the only one
that cannot be faked by anyone else. Deliberately unpolished. If it looks produced, it
loses the thing it is for.

**V6. Results-day cut. 12 seconds, vertical.** &nbsp; *Hook: a results notification lands
on a phone.*

Post it on 2 December 2026 and nowhere near any other day. Notification, then a cut to
the unit page, then the ask, then the URL. The whole idea is timing, and it is the one
video where posting it a week late makes it worthless.

**V7. Six second bumper.** Shield lands, gold rule sweeps, tagline, URL. For story
ad slots and as the end card on everything above. Already essentially built as scene 6
of the promo tour.

**Priority if only three get made:** V1 (free, already shot), V5 (the honesty asset,
one take), V3 (the watchable one). V2 next, because it removes the biggest objection.

---

## 8. Social

**Where.** Instagram and TikTok, same vertical content, same day. Nothing else. A
LinkedIn presence for a student unit review site reaches nobody who will ever write a
review.

**Handle.** `@curtinhonestly` on both if available. Bio: the one-line pitch, the
disclaimer, and a tracked link (`?ref=ig-bio` and `?ref=tiktok-bio`, separately, so you
can tell which platform actually works).

**Four content pillars, in rough order of value:**

1. **The data posts.** Once there is data, this is the whole account. "The five units
   with the highest workload scores this semester." "The unit with the biggest gap
   between rating and workload." A single stat card in brand, one number, one line.
   These are shareable, they are only possible for you, and they get screenshotted into
   group chats, which is distribution you cannot buy. **Do not run these until the
   underlying numbers have enough reviews to be real.** A ranking built on three
   reviews is not a data post, it is a fabrication with a chart on it.
2. **Review quotes.** One anonymous review, set as a card in the site's own style, gold
   rule, stars, tags. Zero production cost, infinite supply once reviews exist, and it
   shows the product without a pitch. Only ever post reviews that do not identify
   anyone.
3. **Build in public.** The count going up. Screenshots of the admin. What broke. What
   got fixed because someone complained. This audience is students, a decent share of
   them are in Computing, and watching someone build something is genuinely interesting
   to them.
4. **The ask.** Direct, timed to results week, no more than one in five posts. An
   account that only asks gets muted.

**Cadence.** Two to three posts a week in normal weeks. Daily from 25 November to 9
December. Nothing during exam weeks except the one post that says good luck and comes
back afterwards.

**The rule that keeps it on brand:** never post a stat you cannot point at a page for.
Every number on this account should be one tap away from the reviews it came from.

---

## 9. Campus tactics and partnerships

**Unit coordinators.** The highest-yield outreach in this plan and it is one email.
Send it in the last two weeks of teaching, one coordinator at a time, with the lecture
slide attached as a PNG.

> Subject: One slide for your last lecture
>
> Hi [name],
>
> I'm a Curtin student and I built curtinhonestly.com, an independent site where
> students leave anonymous reviews of units. It's not affiliated with the University.
>
> I've attached a single slide asking students to write about the unit they just
> finished. If you're willing to leave it up for a few minutes at the end of your last
> lecture, that would help a lot. If not, no problem at all and thanks for reading.
>
> Happy to send you the link first if you'd like to see what students would be writing
> on.
>
> Devon

Short, one ask, an easy no, and the offer to be checked first. Expect a low hit rate and
run it anyway. One yes in a 200-person lecture beats every poster on this list.

**Clubs and societies.** See G2. The mechanic is the partnership. Beyond the draw, ask
for one Discord post and one newsletter line per club, each with its own tracked link.
Offer something back that costs nothing: a stat post about their faculty's units, or a
mention on the site when there is somewhere sensible to put one.

**The Guild.** Worth one honest conversation. The Guild's own interest is students
having better information, which is the same interest as the site's. Possible outcomes:
noticeboard access, a newsletter line, a stall spot at O-Week. Be clear that the site
is independent of both the University and the Guild and that no editorial control is on
offer, because that is the only version of the relationship that keeps the site
credible.

**O-Week, February 2027.** A table, the roll-up of poster P1, a laptop, the coffee
vouchers from G3, and a stack of A5 flyers. First years cannot write reviews yet, so the
job at O-Week is demand and awareness, not supply. The pitch to a first year is "when
you pick your electives next semester, look here first". Give them a sticker.

**Library and study spaces.** Poster P2 during exam and results weeks, on the floors
people actually sit on. This is the closest a poster gets to reaching someone at the
moment they have opinions and nothing to do with them.

---

## 10. Referral links: naming and measurement

Every channel gets a tracking-only link. They cost nothing, they carry no prize and no
requirement, and they are the only way to know which of the things in this document
actually worked.

**Naming convention:** `<channel>-<format>-<placement>`, lowercase, hyphens.

| Slug | Where | Lands on |
| --- | --- | --- |
| `poster-a3-enrol` | Poster P1, enrolment-adjacent walls | Home |
| `poster-a3-zero` | Poster P2, lecture and exam venues | Home |
| `poster-a3-someone` | Poster P3, corridors | Home |
| `slide-lecture` | Lecture slide, generic | Home |
| `slide-comp2004` | Lecture slide, one coordinator's unit | That unit's page |
| `flyer-a5-stall` | O-Week and results-week stall | Home |
| `sticker-laptop` | Stickers | Home |
| `reddit-curtin` | The founder post | Home |
| `ig-bio`, `tiktok-bio` | Social bios | Home |
| `club-<slug>` | One per club | Home |

**URL format.** Every QR generated so far encodes `https://www.curtinhonestly.com/?ref=<slug>`.
The app captures `?ref=` on any page, so a link that should land somewhere other than
home (the `slide-comp2004` variant, for example) just encodes that path with the same
`?ref=` on the end. `https://www.curtinhonestly.com` is the canonical host: the deploy
workflow sets `SITE_URL` to it, and the bare apex 301-redirects to it while preserving
the query string. The codes use
the `www` form directly so the tracking does not depend on that redirect staying
configured. Printed URL text stays as the shorter `curtinhonestly.com`, which is fine,
because nobody types a URL off a poster anyway.

The QR codes in `assets/` already encode the first six, and the codes in all four
rendered pieces have been decoded from the exported PNGs to confirm they scan and carry
the right slug.

**The one number that matters per link is reviews, not visits.** The admin table shows
visits, signups, and reviews per campaign. A link with 400 visits and 2 reviews is a
channel that gets attention and no action, and it should be cut or its copy rewritten.
A link with 30 visits and 11 reviews is the best channel you have. Judge on the third
column.

**Review this table weekly during December.** Kill what does not convert and put the
print budget behind what does.

---

## 11. FAQ and objection handling

Use these answers verbatim on a poster, in a Reddit comment, at the stall, in a DM.
Consistency is the point. Each one is checked against what the site and the legal pages
actually say, because an overclaim here is the one mistake this brand cannot survive.

**"Is this an official Curtin thing?"**
No. It is an independent student platform and it is not affiliated with, endorsed by,
or operated by Curtin University. That is on every page of the site and on every poster.

**"Is it really anonymous?"**
Other students never see your name or your email. They only see whether you are a
verified student. Your review is still linked to your account in the database, which is
what lets you edit and delete it, stops the same account reviewing a unit twice, and
lets abuse reports be investigated. Site administrators can see who wrote a review. All
of that is written out on the privacy page. Worth knowing: a review can identify you
even without your name on it, if you mention a small class or a specific project.

That last sentence is on the privacy page already. Keep saying it. Volunteering the
limitation is what makes the rest of the answer believable.

**"Can a lecturer find out I wrote this?"**
Not through the site. A lecturer can read reviews like anyone else, but no name or
email ever appears next to one, and only site administrators can see who wrote what.
The thing to be careful about is the content of the review itself, in a small class.

**"What stops people writing rubbish, or trashing a unit they failed?"**
One review per account per unit, enforced in the database. A profanity filter. A report
button on every review. Reviews get removed for abuse or for breaking the terms, not
for being negative. And it is an average, so a single bitter review moves the number
very little once a unit has a few.

**"Won't Curtin shut it down?"**
It hosts student opinion, it does not reproduce University material, and it states
clearly that it is not affiliated. Reviews are opinions from students who took the unit.
Anything defamatory about a named individual gets removed on report, which is both the
right thing and the thing that keeps the site safe.

**"Why should I trust the reviews?"**
You should not trust any single one. That is why there are workload scores, grades,
would-take-again percentages, and a review count on every unit. Read five, not one. And
verified students carry a badge, so you can weight accordingly.

**"There's nothing on here."**
Correct, and that is said out loud on a poster rather than hidden. It is new. That is
exactly why the ask is for a review rather than a signup.

**"What do you get out of it?"**
Nothing. No ads, nothing to buy, no sponsor. It is paid for out of pocket by a student
who got tired of picking units blind.

---

## 12. Budget

A realistic first-cycle spend, from now through the December results window.

| Item | Cost |
| --- | --- |
| Posters, 15 x A3 colour, first run | $50 |
| Posters, reprint of what works, 20 more | $70 |
| A5 flyers, 200 | $60 |
| Vinyl stickers, 100 | $65 |
| G1 results-day draw, 5 x $50 vouchers | $250 |
| G2 club partnership prize | $150 |
| G3 coffee vouchers, 50 x $5 | $250 |
| **Total** | **$895** |

**If the budget is $300:** posters ($120), the club partnership ($150), and nothing
else. The club partnership is the best value per dollar in the plan and the posters are
the only always-on channel.

**If the budget is $0:** the lecture slide, the coordinator emails, the Reddit posts,
the Discord posts, the vertical videos, and the tracking links. Every one of those is
free, and honestly they are also the highest-yield items on the list. The money mostly
buys speed.

---

## 13. The next thirty days, concretely

Ordered. Nothing here depends on anything that has not shipped.

1. Modmail r/Curtin. Ask permission. Do this first because the reply time is out of
   your control.
2. Create the tracking-only referral links for every slug in section 10. The printed
   QR codes already point at them, so this has to happen before any printing.
3. **Read the live catalogue.** Units per faculty, and units with at least one review.
   Two numbers, five minutes in the admin. They decide whether poster P2 is still
   truthful and which buildings the print goes in. Do not skip this and then print
   thirty posters.
4. Print five each of P1, P2, P3. Place them. Check in a week whether they are still up.
5. Shoot V5, the founder piece to camera. One take, phone, no music.
6. Post the founder post on r/Curtin once the mods reply. Answer every comment.
7. Recut V1 vertical from the existing promo tour.
8. Email ten unit coordinators with the lecture slide attached. Expect one or two to
   say yes.
9. Approach three clubs with the G2 partnership. Give each their own link.
10. Set up the Instagram and TikTok accounts with tracked bio links. Post V1 and V5.
11. Configure the G1 results-day campaign in the admin now, inactive, so it is ready to
    switch on rather than built in a hurry on 25 November.

Then stop and read the campaign table before spending anything else.

---

## 14. Assumptions, made without asking

1. **The catalogue is 133 units with zero reviews in production**, per
   `plans/versus-pages-seo-spike.md`. If that has changed, section 1 and poster P2's
   expiry condition change with it.
2. **The catalogue is weighted toward Science and Engineering / Computing.** The repo
   name, the brag video's unit choices, and the faculty enum together point that way,
   but it was not verified against live data. **This matters:** if it is true, posters
   should be concentrated in those buildings first, because a Humanities student who
   scans and finds nothing is a worse outcome than one who never scanned. Check the live
   catalogue by faculty before placing print.
3. **Semester 1 2027 O-Week is roughly 9 to 12 February 2027.** Found in search
   results, not confirmed against Curtin's published calendar. The 2026 dates in
   section 3 are from Curtin's own academic calendar and are reliable.
4. **Poster placement needs approval** and unapproved posters get removed. Not verified.
   Hence printing five before thirty.
5. **The budget is small**, in the hundreds not thousands. The $0 and $300 variants are
   there because that assumption may be wrong in either direction.
6. **WA trade promotion conditions as summarised in 5.2 are current.** Confirm at
   wa.gov.au before the first draw. Not legal advice.
7. **"Paid for out of pocket, no sponsor" is currently true.** It is printed on poster
   P3 and answered in the FAQ. If that changes, both change.
8. **Devon is comfortable being the face of it.** V5 and the Reddit posts are built
   around a named founder. If not, V3 and V2 carry the same weight without him on
   camera, but the Reddit posts still need a real account with a real history.
9. **No saved writing-style profile exists.** See section 2. Every draft here would
   improve with one.

---

## 15. How this was built

Devon asked for iteration rather than a first draft, so, briefly, what happened:

- **Fourteen poster concepts** were drafted. Six were kept, four were rendered as real
  on-brand HTML at true print dimensions. The seven that were cut are in section 4.3
  with the reason, so they do not come back around in six months.
- **Seven giveaway mechanics** were drafted against the actual campaign fields in the
  admin. Five were kept, four cut in section 5.4. The first draft of the giveaway
  section assumed a prize mechanic would need building. It does not. Reading
  `admin-frontend/src/app/pages/campaigns/` changed the section from a wishlist into a
  configuration.
- **The Reddit section was rewritten** once the real risk was identified. "The account
  could get banned" is not the risk. Domain-level filtering is, and it takes the honest
  route down with it. That reframing is why section 6.1 leads rather than sits in a
  footnote.
- **The timing section was rebuilt** around `results-season.util.ts`. The first version
  said "launch at semester start", which is both a stale default and wrong for this
  product. The product already knows when reviews get written. The calendar now matches
  the code.
- **The first render of poster 01 was thrown out.** The shield asset had grey border
  artefacts, the lower third was dead space, and the layout had no payoff line. The
  shield was re-cut against the brand gold value, and a payoff line was added to anchor
  the bottom third on all three posters.
