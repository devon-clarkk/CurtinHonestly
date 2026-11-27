# Launch runbook, Semester 2 2026

What to do and when, from now to Wednesday 16 December 2026. Dates are 2026,
times Perth (AWST). Owner is Devon unless a step says "agent", meaning a code or
config change that can be delegated and reviewed.

This supersedes the dates in `launch-plan.md` section 13 ("the next thirty days",
written in August). Every asset it points at already exists in this folder; the
few new ones are marked (new). Nothing here deploys the site; the site changes in
section 1 are ordinary pull requests into `dev`.

## 0. Decisions first (about an hour)

1. **Giveaway.** Confirm G1 (five $50 vouchers, 200-character minimum, five
   entries, verified only, 14 Nov to 12 Dec) and G2 ($150 club prize) from
   `giveaway-plan-2026-s2.md`. Pick the voucher type.
2. **Who can post.** The backend lets any account post (no verified check in
   `ReviewService`); a student email is verified at registration and any other
   email can verify later from the Account page. The About page says "to post,
   you confirm a student address" and the README says registration requires
   one. Both are wrong today. Recommended rule: posting open, verified reviews
   labelled, prize entries verified only. Copy changes only. Enforcing
   verified-only posting is a backend change weeks before launch and shrinks the
   reviewer pool; not recommended for this campaign.
3. **OASIS checks.** Study-week and eVALUate dates. No copy mentions eVALUate
   until confirmed.
4. **Legal check.** Work through the table in `giveaway-plan-2026-s2.md` before
   any prize is announced anywhere.
5. **Poster permission.** Guild (clubs and marketing) and Curtin Properties.
   Plan 4.4: print five, see if they survive a week, then print the rest.
6. **Numbers.** Read the live catalogue (plan 13.3): reviews by faculty, units
   with reviews. They decide where print goes and they replace "133 units" in
   Reddit post A and LinkedIn post 1.

## 1. Pre-launch blockers (clear by Mon 2 Nov)

### 1.1 Cloudflare apex redirect loop and HSTS

Verified 20 October with curl: `http://curtinhonestly.com` returns 301 to
itself; `https://curtinhonestly.com` redirects correctly to the `www` host; the
`www` host sends `Strict-Transport-Security: max-age=10886400; includeSubDomains; preload`.
Plan section 10 assumed the apex redirect preserved the query string. The
printed codes already use the `www` host, so no asset depends on this, but the
loop still has to go.

In the Cloudflare dashboard for the zone:

1. SSL/TLS, Edge Certificates: turn on Always Use HTTPS, so `http://` on the
   apex is upgraded before any redirect rule runs.
2. Rules, Redirect Rules: find the rule that sends the apex to itself. It should
   match the apex host and redirect to `https://www.curtinhonestly.com` plus the
   request path, status 301, preserving the query string. Remove any rule whose
   target host is the apex.
3. SSL/TLS, Edge Certificates: enable HSTS for the zone, 12 months, include
   subdomains. Leave preload off until the apex has been stable for a month.
4. Test all four forms with `curl -sI` (`http` and `https`, apex and `www`). Each
   ends at `https://www.curtinhonestly.com/` and the apex carries the header.
5. Test `http://curtinhonestly.com/?ref=poster-a3-zero` lands on `www` with the
   query intact.

### 1.2 Make three surfaces agree on who can post (agent)

With the recommended rule from decision 2:

- `frontend/src/app/components/info/about.component.html` line 46: "To post, you
  confirm an @student.curtin.edu.au email address" becomes "Anyone can create an
  account and post. To carry the Verified Curtin Student label, you confirm an
  @student.curtin.edu.au email address."
- `README.md` line 123: "Registration requires a @student.curtin.edu.au email"
  becomes "Registration accepts any email. An @student.curtin.edu.au address is
  verified immediately and labels the account's reviews Verified Curtin Student;
  other accounts can verify later from the Account page."
- Register page: hint is already right. Add under the promo code hint: "Prize
  draw entries require a verified student account."
- Plan 11 FAQ and poster 03's "who writes it" row stay true under this rule.

### 1.3 Retime the seasonal banner (agent)

`frontend/src/app/utils/results-season.util.ts` opens 25 November and closes
9 December, which misses the fortnight after exams and overruns phase 2. Two
windows, each with its own line:

| Window | Banner | Link |
| --- | --- | --- |
| 2 Nov to 1 Dec | Finished a unit? Review it while it's fresh. | `/my-reviews`, or `/sem2` once it exists |
| 2 Dec to 16 Dec | Results are out. Which units did you just finish? Write a review. | same |

Keep June's window for Semester 1. Confirm a signed-out visitor can get from the
banner to a unit and the review form without a dead end.

### 1.4 Public campaign page and campaign terms (agent)

The mechanic is built; nothing a student sees before signing up explains the
prize. Add a `/sem2` route, prerendered like `/about`, with: the headline
"Semester 2 review draw"; prize, dates, draw date; who can enter and what
qualifies (giveaway plan, entry rules); a live counter of reviews since
14 November and units reviewed; one "Write a review" button; the campaign
terms (Appendix A) under their own heading; the standing line. Link it from the
banner, the Register promo-code hint and the Account page campaign card. After
it ships, the QR codes can stay as they are: referral capture is site-wide and
the home page carries the banner.

### 1.5 Analytics (agent)

Confirm or add GA4 events `review_submitted` and `campaign_page_view`. Confirm
the admin Campaigns page shows visits, signups and reviews against a test slug.

## 2. Admin setup (Fri 30 Oct, 30 minutes)

1. Create campaign G1 with the fields in `giveaway-plan-2026-s2.md`.
2. Create tracking-only referral links (plan section 10 naming):
   `poster-a3-enrol`, `poster-a3-zero`, `poster-a3-someone`, `poster-a3-results`
   (new), `slide-lecture`, `flyer-a5-stall`, `sticker-laptop`, `story-zero`,
   `story-someone`, `story-results`, `story-rant` (new), `reddit-curtin`,
   `ig-bio`, `tiktok-bio`, `linkedin-devon`, `press-grok`, `club-comssa`,
   `club-cec`, `club-cdsa`, `club-robotics`. Tick G1 enrolment on the society
   and story links; leave the poster links tracking-only unless you want
   poster scans to join the draw too (they can; the Account page then shows the
   campaign).
3. Per-unit slide slugs (`slide-<unitcode>`) as each coordinator says yes.
4. Test in a private window: open a poster URL, register a test student
   account, write a 200-character review, confirm visit, signup, review and
   entry all appear. Remove the test data.
5. Confirm `uk_reviews_user_unit` exists in production.

## 3. Print and stage (Mon 2 Nov to Fri 6 Nov)

1. `cd design/marketing` then `python export.py` (add `--noprize` if the legal
   check is still open; the prize sentence disappears and nothing else moves).
2. Print one A4 proof of posters 02, 03 and 04 and the flyer. Scan each QR with
   two phones; check the visit lands on the right slug.
3. Order per `giveaway-plan-2026-s2.md`: 30 A3 (02 and 03), 12 A3 (04 and the
   phase 1 winner, can wait until 1 Dec), 200 A5 flyer A. Supply `exports/*.pdf`.
4. Placement plan from `poster-ab-test.md` (pairs per board). Print the log.
5. Stage every post from `linkedin/posts-drafts.md`, plan section 6, and
   `copy-additions-2026-s2.md` as dated drafts.

## 4. Calendar

Rule from the backlinks plan: never two overlapping communities within 24 hours.

### Before exams (now to Fri 30 Oct)

| Date | Action | Asset |
| --- | --- | --- |
| This week | Modmail r/Curtin (plan 6.2). Reply time is out of your control, so first. | Plan 6.2 |
| By Fri 30 Oct | Email ten unit coordinators with the slide PNG (plan 9). Last two weeks of teaching. One per email. | `exports/slide-lecture-16x9.png`, plan 9 email |
| By Fri 30 Oct | Admin setup (section 2). Site fixes in review. | |
| Any day | Shoot V5, the founder piece to camera (plan 7). One take. | |

### Exam fortnight (Mon 2 Nov to Fri 13 Nov)

| Date | Action |
| --- | --- |
| Mon 2 Nov | Banner phase 1 live. `/sem2` live. Cloudflare fixed. Nothing posted (plan 3). |
| Mon 9 Nov | Posters 02 and 03 up in pairs: library floors, Engineering Pavilion, Building 314, Building 204, the Hub, Guild building, housing boards (with permission). Photograph each spot. Flyers on library study floors. |
| Thu 12 Nov | Check, log, replace. |
| Fri 13 Nov | Exams end. All drafts ready. |

### Phase 1: review it while it's fresh (Sat 14 Nov to Tue 1 Dec)

| Date | Channel | Asset and slug |
| --- | --- | --- |
| Sat 14 Nov | G1 opens. Share caption 1 to any user mailing path that exists (verify; admin alerts exist, user mailing was not checked). | Copy additions, section 6 |
| Mon 16 Nov | **ComSSA**: email `club@comssa.org.au` and open the PR adding a Resources item on `ComSSA/comssa.github.io`. Ask for a Discord post this week. | Copy additions 1 and 2, `club-comssa`, story-rant image |
| Tue 17 Nov | **r/Curtin post A** (if mods agreed), with the numbers updated. Answer every comment for 48 hours. | Plan 6.3, `reddit-curtin` |
| Wed 18 Nov | **Curtin Engineers Club**: PR to `Curtin-Engineers-Club/CECWebsite` (`docs/data/linksData.json` plus a tile image) and email `contact@cec.curtin.edu.au`. | Copy additions 1, `club-cec` |
| Thu 19 Nov | **LinkedIn post 1** (updated for 1,761 units and the live review count). Instagram story: story-zero. | `linkedin/posts-drafts.md`, `linkedin-devon`, `story-zero` |
| Fri 20 Nov | **CDSA**: email via curtindata.com and the TidyHQ contact page; ask for a line in the club description on the Guild page. | `club-cdsa` |
| Mon 23 Nov | **Robotics**: email via curtinrobotics.org. Swap poster sides on every board (A/B midpoint). | `club-robotics` |
| Wed 25 Nov | **r/Curtin post B**, the genuine question with disclosure. | Plan 6.4 |
| Fri 27 Nov | Instagram story: story-someone. Check posters, log, replace. First G2 standings to the clubs. | `story-someone` |
| Mon 30 Nov | Weekly numbers (section 6). Order poster 04 and the phase 1 winner. | |
| Tue 1 Dec | Phase 1 ends. Read the A/B numbers once (`poster-ab-test.md`). | |

### Phase 2: results are out (Wed 2 Dec to Wed 16 Dec)

| Date | Channel | Asset and slug |
| --- | --- | --- |
| Wed 2 Dec | Results. Banner phase 2 live. **LinkedIn post 3** as written. **r/Curtin post C** (prize "until the 12th"). Discord posts to every society that said yes with story-results. Poster 04 up with the phase 1 winner in library, Guild, housing, the Hub. V6 if made. | `posts-drafts.md` post 3, plan 6.5, copy additions 2, `story-results`, `poster-a3-results` |
| Thu 3 Dec | One-line follow-ups to societies that have not replied. G2 standings. | Copy additions 1 |
| Mon 7 Dec | Weekly numbers. If reviews are past 120, send the press note to the Guild comms team and `Grok.Editor@guild.curtin.edu.au`. | Copy additions 4, `press-grok` |
| Thu 10 Dec | Last call: story-results again, Discord last-call line. | Copy additions 2 |
| Sat 12 Dec 23:59 | G1 closes. Set the campaign inactive at 00:00 Sunday. G2 standings frozen. | |
| Mon 14 Dec | Draw per the giveaway plan. Publish the winning tokens. Email winners and the G2 club. | |
| Wed 16 Dec | **LinkedIn post** and Reddit post D shape: real numbers, including the bad ones, thanks to societies by name with permission. Record the numbers in Appendix B. | Plan 6.5 post D |

## 5. Moderation during both phases

The report flow exists; plan section 11 has the answers to give. During the
campaign:

- Check the admin reports queue twice a day, and within an hour of any society
  post or Reddit post going live.
- Remove on sight: anything about a staff member's private life, appearance or
  protected attributes; anything naming another student; threats; doxxing.
  Staff may be named only in relation to their teaching of that unit.
- Remove or keep; never edit. Email the author only when a removal needs a
  reason beyond the Terms.
- Removed reviews forfeit their entries (Terms already say so).
- Log date, review id, reason, action.
- If a thread turns on the platform: answer facts once, do not argue, link the
  About page.

## 6. Measurement

Every Monday, ten minutes: reviews total, by faculty, units with 1 and with 3
or more reviews, visits, signups and reviews per slug (admin Campaigns page),
G2 standings, open reports, sheets still up. Fill Appendix B. A/B decisions on
Tue 1 Dec and Wed 16 Dec. Final read against the targets in
`executive-summary-2026-s2.md`.

## 7. Templates

- Coordinator email: plan section 9.
- Modmail and Reddit posts A, B, C, D: plan section 6.
- LinkedIn posts: `linkedin/posts-drafts.md` (update post 1's numbers; post 3
  runs on 2 Dec as written).
- FAQ answers for comments and DMs: plan section 11.
- Society email, Discord posts, Instagram captions, press note, share captions,
  site copy: `copy-additions-2026-s2.md`.
- Campaign terms: Appendix A below.

---

## Appendix A: campaign terms (draft for the /sem2 page)

Sits under the site Terms, "Promotions and prize draws", which says campaign
terms prevail. Complete the legal check in `giveaway-plan-2026-s2.md` before
publishing and fill the bracketed values.

**Semester 2 2026 review draw: campaign terms**

1. **Promoter.** CurtinHonestly, an independent student platform operated by
   Devon Clark, contact@curtinhonestly.com. CurtinHonestly is not affiliated
   with, endorsed by or operated by Curtin University.
2. **Period.** Entries open 12:00 am AWST on Saturday 14 November 2026 and close
   11:59 pm AWST on Saturday 12 December 2026.
3. **Who can enter.** Account holders whose account is verified with an
   @student.curtin.edu.au email address at the time of the draw. The promoter
   and members of the promoter's household cannot enter. [Add an age condition
   if the legal check requires one.]
4. **How to enter.** Publish a review of a Curtin unit you have taken on
   www.curtinhonestly.com during the period that is at least 200 characters of
   review text, is your only review of that unit, and complies with the site
   Terms and moderation rules. Each qualifying review is one entry, up to five
   entries per person. Entry is free.
5. **What does not count.** Reviews removed under the moderation rules,
   duplicate or low-effort reviews, and reviews written solely to gain entries
   earn no entries, and entries already recorded for them are withdrawn.
   Deleting a review removes its entry.
6. **Prizes.** Five prizes, each a $50 [voucher type] gift card, total prize
   pool $250. One prize per person. Prizes are not exchangeable for cash except
   where the prize is itself a cash-equivalent card.
7. **Draw.** Conducted in Perth, Western Australia at [time] on Monday
   14 December 2026 by random selection from all valid entry tokens using a
   recorded random process. The five winning tokens are published on this page
   on the day of the draw; entrants can compare them with the tokens shown on
   their Account page.
8. **Notification.** Winners are also emailed at their account address within
   two days of the draw and have seven days to respond with a delivery email
   address. An unclaimed prize is redrawn on [date] on the same terms and the
   new winning token published.
9. **Records.** Draw records are kept for 12 months and are available on
   request to [the regulator named by the legal check].
10. **Privacy.** Entry data is the account and review data already held under
    the Privacy Policy. Email addresses are used only to run the draw and
    deliver prizes.
11. **Changes.** If the campaign cannot run as planned for reasons outside the
    promoter's control, the promoter may change or cancel it and will publish
    the change here. Where these terms conflict with the general Terms, these
    terms prevail for this campaign.

## Appendix B: weekly numbers

| Week ending | Reviews | By faculty (SE / BL / HS / Hum / AS) | Units with 1+ / 3+ | Visits by slug (top 3) | Signups | Reviews by slug (top 3) | G2 leader | Open reports | Sheets up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Mon 16 Nov | | | | | | | | | |
| Mon 23 Nov | | | | | | | | | |
| Mon 30 Nov | | | | | | | | | |
| Mon 7 Dec | | | | | | | | | |
| Mon 14 Dec | | | | | | | | | |
| Wed 16 Dec (final) | | | | | | | | | |
