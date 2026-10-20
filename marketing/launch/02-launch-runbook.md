# Launch runbook: CurtinHonestly Semester 2 2026

What to do and when, in order. Dates are 2026 and times are Perth (AWST).
Owner is Devon unless a step says "agent", which means it is a code or config
change that can be delegated and reviewed.

## 0. Decisions to make before anything else (about an hour)

1. **Prize and rules.** Pick an option from `05-giveaway-plan.md`. Decide the
   prize, the minimum review length (recommended 120 characters), the maximum
   entries per person (default 5), verified students only (yes), open
   14 Nov 00:00, close 12 Dec 23:59, draw 14 Dec.
2. **Who can post.** The backend lets any account post a review (no verified
   check in `ReviewService`); a student email is verified at registration, any
   other email can verify later from the Account page. The About page says "to
   post, you confirm a student address" and the README says registration
   requires one. Both are wrong today. Pick one rule and make every surface say
   it. Recommended: keep posting open, label verified reviews, require verified
   for prize entries. It needs copy changes only. The alternative (enforce
   verified-only posting) is a backend change two weeks before launch and cuts
   the pool of potential reviewers; not recommended for this campaign.
3. **Calendar checks in OASIS.** Study-week dates and the eVALUate open and
   close dates. No copy mentions eVALUate until they are confirmed.
4. **Trade-promotion check.** Read the Western Australian conditions for trade
   promotion lotteries before any prize is announced (see
   `05-giveaway-plan.md`, section "Legal check"). Record the outcome in that
   file.
5. **Campus poster permission.** Ask the Guild (clubs and marketing) and Curtin
   Properties what third-party or student-project posters are allowed where.
   Get it in writing. Ask ComSSA whether a co-hosted poster can go on society
   boards.

## 1. Pre-launch blockers (clear by Mon 2 Nov)

### 1.1 Cloudflare apex redirect loop and HSTS

Verified 20 October: `http://curtinhonestly.com` returns 301 to itself
(`Location: http://curtinhonestly.com/`), a loop. `https://curtinhonestly.com`
redirects correctly to `https://www.curtinhonestly.com`. The `www` host sends
`Strict-Transport-Security: max-age=10886400; includeSubDomains; preload`.

Fix, in the Cloudflare dashboard for the zone:

1. SSL/TLS, Edge Certificates: turn on **Always Use HTTPS**. This upgrades
   `http://curtinhonestly.com` to `https://` at the edge before any redirect
   rule runs.
2. Rules, Redirect Rules: find the rule that sends the apex to itself. It
   should match `(http.host eq "curtinhonestly.com")` and redirect to
   `concat("https://www.curtinhonestly.com", http.request.uri.path)` with
   status 301, preserving the query string (this matters for `?ref=`). Remove
   or correct any rule whose target host is the apex.
3. SSL/TLS, Edge Certificates: turn on **HSTS** for the zone with
   `max-age` 12 months, include subdomains, so the apex also sends the header.
   Leave "preload" off until the apex has been stable for a month.
4. Test all four forms with `curl -sI`: `http://` and `https://` on apex and
   `www`. Each must end at `https://www.curtinhonestly.com/` and the apex must
   carry the HSTS header.
5. Test that `http://curtinhonestly.com/?ref=poster-rant` lands on the `www`
   site with `?ref=poster-rant` intact.

Until this is done, every printed URL and QR uses the `https://www` form, which
the posters already do.

### 1.2 Reconcile who can post (agent, one evening)

Apply decision 2 above. With the recommended rule:

- `frontend/src/app/components/info/about.component.html` line 46: replace
  "To post, you confirm an @student.curtin.edu.au email address" with "Anyone
  can create an account and post. To carry the Verified Curtin Student label,
  you confirm an @student.curtin.edu.au email address."
- `README.md` line 123: "Registration requires a @student.curtin.edu.au email"
  becomes "Registration accepts any email. An @student.curtin.edu.au address is
  verified immediately and labels the account's reviews Verified Curtin
  Student; other accounts can verify later from the Account page."
- Register page hint is already correct. Add one sentence under the promo code
  hint: "Prize-draw entries require a verified student account."
- Terms, "Promotions and prize draws": no change needed; the campaign terms
  (Appendix A) do the rest.

### 1.3 Seasonal banner retimed (agent)

`frontend/src/app/utils/results-season.util.ts` opens the banner on
25 November and closes 9 December, which misses phase 1 entirely and overruns
phase 2. Replace the single window with two, each with its own line:

| Window | Banner text | Link |
|---|---|---|
| 2 Nov to 1 Dec | Finished a unit? Review it while it's fresh. | /my-reviews (or /sem2 once it exists) |
| 2 Dec to 16 Dec | Results are out. Which units did you just finish? Write a review. | same |

Keep June's window for Semester 1 (adjust to the 2027 calendar when known).
Confirm that the banner's link lets a signed-out visitor reach a unit and the
review form without a dead end; if `/my-reviews` bounces to login and loses the
intent, link the banner to the home search with a prompt instead.

### 1.4 Public campaign page and campaign terms (agent)

The mechanic is built (promo codes, referral links, entries, caps, close date,
Account page progress) but nothing a student sees before signing up explains
the prize. Add a `/sem2` route (prerendered like `/about`) containing, in this
order:

1. Headline: "Semester 2 review drive". Sub: "Write what the unit was actually
   like. Every qualifying review is an entry in the prize draw."
2. The prize, the open and close dates and the draw date.
3. Who can enter and what qualifies (from Appendix A, in plain words).
4. A live counter: reviews written since 14 November, and units reviewed. The
   admin overview already computes totals; expose a public read-only count.
5. One button: "Write a review" (to the unit search with the campaign code
   pre-applied, or to /register with `?code=SEM2-2026`).
6. The campaign terms (Appendix A) in full, below a heading "Campaign terms".
7. The footer line: "Independent student platform. Not affiliated with Curtin
   University."

Link it from the banner, from the Register page's promo-code hint, and from the
Account page's campaign card. Once it exists, rebuild the posters with
`python build.py --base https://www.curtinhonestly.com/sem2` so the QR codes
land on it, and re-scan a proof.

### 1.5 Seasonal home share card (agent, optional)

`frontend/scripts/generate-seo-assets.js` produces the home OG card. Add a
seasonal variant for the campaign window ("Semester 2 is done. Review your
units.") so every link shared in Discord renders the campaign message. Unit
cards keep "Be the first to review this unit".

### 1.6 Analytics (agent)

GA4 is wired. Confirm or add two events: `review_submitted` and
`campaign_page_view`. Confirm the admin Campaigns page shows visits, signups and
reviews per referral link against a test slug before any slug is printed.

## 2. Campaign setup in the admin UI (Fri 30 Oct, 30 minutes)

In the admin dashboard, Campaigns page:

1. **Create the campaign.** Name "Semester 2 2026 review drive". Code
   `SEM2-2026`. Slug `sem2-2026`. Prize description: the chosen prize in one
   line. Starts 14 Nov 2026 00:00 AWST (enter as UTC 13 Nov 16:00). Ends
   12 Dec 2026 23:59 AWST (12 Dec 15:59 UTC). Min review length 120. Max entries
   per user 5. Require verified student: on. Required review count 1. Likes
   thresholds 0. Max redemptions blank.
2. **Create referral links**, each ticked to enrol signups into the campaign:
   `poster-rant`, `poster-first`, `poster-results`, `social-rant`,
   `social-first`, `social-results`, `comssa`, `cec`, `cdsa`, `robotics`,
   `linkedin`, `instagram`, `rcurtin`, `grok`, `email`. Landing path `/` for
   now, `/sem2` once the page exists.
3. **Test** with a private browser window: open
   `https://www.curtinhonestly.com/?ref=poster-rant`, register a test student
   account, write a test review of 120 or more characters on a unit, confirm
   the visit, signup and review appear against `poster-rant` and the entry
   appears on the test account's Account page. Delete the test review and
   account afterwards (or mark them in admin) so they do not count.
4. **Duplicate-review constraint.** The campaign runbook in
   `plans/campaign-promo-codes-runbook.md` requires the unique
   (user, unit) constraint to be present in production. Confirm it with the
   SQL in that file before the campaign opens.

## 3. Print and stage (Mon 2 Nov to Fri 6 Nov)

1. Rebuild the posters if anything changed: `cd marketing/launch/03-posters`
   then `python build.py` (add `--no-prize` if the prize is not confirmed; the
   strip disappears and nothing else moves).
2. Print one A4 proof of each design. Scan each QR with two phones. Check the
   visit lands against the right slug. Check the footer line is legible.
3. Place the print order (quantities and costs in `05-giveaway-plan.md`).
   Supply the `export/*-A3.pdf` and `export/*-A4.pdf` files; they are vector
   with fonts embedded. Ask for 150 to 200 gsm uncoated for posters.
4. Write the placement plan from `06-ab-test-plan.md` (pairs per board, or
   rotation groups). Print the log sheet.
5. Stage the copy: paste every post from `04-copy-pack.md` into drafts with
   dates, so each day in the calendar below is a two-minute job.

## 4. Calendar

Rule from the backlinks plan: never two overlapping communities within
24 hours. Computing societies share members; space them out.

### Exam fortnight (Mon 2 Nov to Fri 13 Nov)

| Date | Action |
|---|---|
| Mon 2 Nov | Banner phase 1 live. `/sem2` live. Cloudflare fixed. Campaign active in admin (entries start 14 Nov, but the page can say "opens 14 November"). |
| Mon 9 Nov | Posters A and B up in pairs: library, Engineering Pavilion, Building 314 (computing), Building 204, the Hub, Guild building, student housing boards (with permission). Photograph each spot. Flyers in the library study areas. |
| Thu 12 Nov | Check posters, log removals, replace. |
| Fri 13 Nov | Exams end. Nothing posted. Confirm every draft is ready. |

### Phase 1: "Review it while it's fresh" (Sat 14 Nov to Tue 1 Dec)

| Date | Channel | Asset |
|---|---|---|
| Sat 14 Nov | Campaign opens. Share caption 1 to any existing user list if the platform can email accounts (verify the path; admin alerts exist, user mailing was not verified). | Share captions |
| Mon 16 Nov | **ComSSA**: email `club@comssa.org.au` and open the GitHub PR adding a Resources item on `ComSSA/comssa.github.io`. Ask for a Discord post the same week. | Society email, Discord post, slug `comssa` |
| Tue 17 Nov | **LinkedIn post 1** from Devon. | Copy pack, slug `linkedin` |
| Wed 18 Nov | **Curtin Engineers Club**: PR to `Curtin-Engineers-Club/CECWebsite` adding one object to `docs/data/linksData.json` with a tile image, plus email `contact@cec.curtin.edu.au`. | Society email, slug `cec` |
| Thu 19 Nov | **Instagram** feed post 1 with the 9:16 image A as a story. | Copy pack, slug `instagram` |
| Fri 20 Nov | **CDSA**: email via `curtindata.com` contact and the TidyHQ contact page; ask for a line in the club description on the Guild page. | Society email, slug `cdsa` |
| Mon 23 Nov | **Robotics Club**: email via `curtinrobotics.org`. Swap poster sides on every board (A/B midpoint). | Society email, slug `robotics` |
| Wed 25 Nov | **r/Curtin** post with disclosure. Watch the thread for 48 hours and answer every question. | Copy pack, slug `rcurtin` |
| Fri 27 Nov | Instagram story 2 (image B). Check posters, log, replace. | |
| Mon 30 Nov | Weekly numbers. Decide the poster winner per the A/B plan (look once on Tue 1 Dec). | |
| Tue 1 Dec | Phase 1 ends. Print poster C and the phase 1 winner for phase 2 if the first run is depleted. | |

### Phase 2: "Results are out" (Wed 2 Dec to Wed 16 Dec)

| Date | Channel | Asset |
|---|---|---|
| Wed 2 Dec | Results day. Banner phase 2 live. **LinkedIn post 2.** Instagram story (image C). Discord posts to any society that said yes, image C. Poster C up alongside the phase 1 winner in the spots that still have traffic (library, Guild, housing, the Hub). | Slugs `linkedin`, `instagram`, society slugs |
| Thu 3 Dec | Society follow-up emails to anyone who has not replied (one line, no pressure). | |
| Mon 7 Dec | Weekly numbers. If reviews are past 120, send the press note to the Guild comms team and `Grok.Editor@guild.curtin.edu.au`. | Press note, slug `grok` |
| Thu 10 Dec | "Two days left" story and Discord posts. | Share caption 2 |
| Sat 12 Dec 23:59 | Draw closes. Set the campaign inactive in admin at 00:00 Sunday. | |
| Mon 14 Dec | Draw (method in `05-giveaway-plan.md`). Email winners from `contact@curtinhonestly.com`. | |
| Wed 16 Dec | **LinkedIn post 3** and Instagram post: results of the drive, faculties opened, thanks to the societies by name (with their permission). Record the final numbers in this folder. | |

## 5. Moderation during the campaign

The report flow exists. During both phases:

- Check the admin reports queue twice a day (morning and evening), and within
  an hour of any society post going live.
- Remove on sight: anything about a staff member's private life, appearance or
  protected attributes; anything naming another student; threats; doxxing.
  Staff may be named only in relation to their teaching of that unit.
- Edit nothing. Remove or keep. Email the author only when a removal needs a
  reason beyond the Terms.
- Reviews removed under moderation forfeit their draw entries (Terms already
  say so).
- Keep a short log: date, review id, reason, action. It is the record if a
  complaint arrives.
- If a thread on r/Curtin or a Discord turns on the platform, answer facts once,
  do not argue, and point to the About page.

## 6. Measurement

- Every Monday: total reviews, reviews by faculty, units reviewed, visits,
  signups and reviews per slug (admin Campaigns page), open reports, posters
  still up. Ten minutes. Write the numbers into a table at the bottom of this
  file.
- A/B decision points: Tue 1 Dec and Wed 16 Dec, per `06-ab-test-plan.md`.
- Final: compare against the targets in `01-executive-summary.md`. The number
  that matters for next semester is reviews by faculty.

## 7. Templates

The society email, press note and all posts are in `04-copy-pack.md`. Campaign
terms are below so the page can be built from them.

---

## Appendix A: campaign terms (draft for the /sem2 page)

These sit under the site's general Terms, section "Promotions and prize draws",
which says campaign-specific terms prevail. Review against the Western
Australian trade-promotion conditions before publishing (see
`05-giveaway-plan.md`). Fill the bracketed values.

**Semester 2 2026 review drive: campaign terms**

1. **Promoter.** CurtinHonestly, an independent student platform operated by
   Devon Clark, contact@curtinhonestly.com. CurtinHonestly is not affiliated
   with, endorsed by or operated by Curtin University.
2. **Period.** Entries open 12:00 am AWST on Saturday 14 November 2026 and close
   11:59 pm AWST on Saturday 12 December 2026.
3. **Who can enter.** Account holders whose account is verified with an
   @student.curtin.edu.au email address at the time of the draw. The promoter
   and members of the promoter's household cannot enter. [Confirm whether
   entrants must be 18 or over; if so, add it here.]
4. **How to enter.** Publish a review of a Curtin unit on
   www.curtinhonestly.com during the period that meets all of: at least
   120 characters of review text; one review per unit per person; written
   about a unit the entrant has taken; compliant with the site Terms and
   moderation rules. Each qualifying review earns one entry, up to five
   entries per person. No purchase is necessary and entry is free.
5. **What does not count.** Reviews removed under the moderation rules,
   duplicate or low-effort reviews, and reviews written solely to gain entries
   earn no entries, and entries already recorded for them are withdrawn.
   Deleting a review removes its entry.
6. **Prizes.** [Number and description of each prize, with its retail value in
   AUD, for example "One $150 gift card and three $50 gift cards, total prize
   pool $300".] Prizes are not transferable or exchangeable for cash except
   where the prize is itself a cash-equivalent card.
7. **Draw.** Conducted at [place, for example "Perth, Western Australia"] at
   [time] on Monday 14 December 2026 by random selection from all valid entries
   using a recorded random process. The first entry drawn wins the first prize
   and so on. One prize per person.
8. **Notification.** Winners are emailed at their account address within two
   days of the draw and must respond within seven days with a delivery email
   address for the gift card. If a prize is unclaimed after seven days, a
   redraw is held on [date] on the same terms. Winners' reviewer handles, not
   names or emails, may be published on the campaign page.
9. **Privacy.** Entry data is the account and review data already held under
   the Privacy Policy. Email addresses are used only to run the draw and
   deliver prizes.
10. **Changes.** If the campaign cannot run as planned for reasons outside the
    promoter's control, the promoter may change or cancel it and will publish
    the change on this page. Where these terms conflict with the general
    Terms, these terms prevail for this campaign.

## Appendix B: weekly numbers (fill in)

| Week ending | Reviews total | By faculty (SE / BL / HS / Hum / AS) | Units reviewed | Visits by slug (top 3) | Signups | Reviews by slug (top 3) | Open reports | Posters up |
|---|---|---|---|---|---|---|---|---|
| Mon 16 Nov | | | | | | | | |
| Mon 23 Nov | | | | | | | | |
| Mon 30 Nov | | | | | | | | |
| Mon 7 Dec | | | | | | | | |
| Mon 14 Dec | | | | | | | | |
| Wed 16 Dec (final) | | | | | | | | |
