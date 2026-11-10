# CurtinHonestly: four new promo video concepts

Written 28 August 2026. **These are concepts for Devon to pick from and refine.
Nothing here is commissioned, nothing has been rendered, and no video skill was
invoked.** Pick one, or take a scene from one and graft it onto another.

Companion to what already exists, and deliberately not a recut of either:

| Existing | What it already owns |
|---|---|
| `design/brag-launch-video/` (`brag.mp4`, 20s) | The hook and highlights cut: "Which units are actually worth it?", search type-on, three stat cards, dark outro |
| `design/brag-promo-tour/` (`brag.mp4`, 33s) | The site tour: generic search, click through, scroll the catalogue, one unit deep dive, closing lockup |

Each concept opens with a **What it owns** line naming the beat or claim neither
existing film has. If a concept stops owning that line it has become a recut.

---

## Read this before building any of them

### Shootability, honestly

Section 1 of `launch-plan.md` records production at **133 units and zero reviews**, and
sequences the whole campaign supply first: review-asking material goes up before
answer-promising material, and the demand-side pieces earn their budget once roughly 40
units clear three or more reviews. That is a real constraint on what can be filmed
today, so every concept below is tagged:

| Concept | Tag |
|---|---|
| 1. Results are out | **Shootable now**, real UI, no review content required |
| 2. Ninety seconds, then it is out of your head | **Shootable now**, the add-review form is the hero |
| 3. Anonymous, and here is exactly what that means | **Shootable now**, real account and privacy screens |
| 4. Two descriptions | **Needs seeded review data**, and is the one to hold until supply lands |

### The statistics rule, which the existing films did not follow

`design/marketing/README.md` house rule 4: never invent statistics about a real named
unit. Either use live numbers with the review count shown, or leave the slot blank on
purpose. Both existing storyboards carry invented figures against real Curtin unit codes
(COMP2004 at 4.6 stars with 91 percent would-take-again, and others), labelled
"representative".

**These concepts follow the rule rather than the precedent.** Every metric below is
written as a live-data slot, filled from the database at shoot time, with the reviewer
count visible beside it. Where no live number exists yet, the frame shows the real empty
state instead of a mocked full one. Empty states are on brand: the site already says
"Looking for a unit we don't have yet? Let us know."

### House constraints for all four

From `launch-plan.md` section 2 and `design/marketing/README.md`.

- **Format.** 1920 x 1080, 25 to 35s, music and SFX, no voiceover. A vertical cut for
  socials is a second pass, not a first.
- **Palette.** Background `#FAFAFA`, ink `#1F1F1F`, gold `#C09711` **fill only**, gold
  ink `#8A6D0C` for any gold text on a light surface, black `#000000`, muted on dark
  `#CFCFCF`, grey `#666666`, hairline `#E8E8E8` at 1px.
- **Type.** Roboto Condensed for display and labels, Rubik for body. Sharp corners, no
  radius. No gradients, no drop shadows, no rounded pill buttons. The site looks like a
  handbook on purpose.
- **Logo.** Never place `logo.png` on the off-white background: it has an opaque black
  slab baked into the artwork. Use `assets/shield-gold.png` (gold shield, transparent)
  with live text, "Curtin" in ink and "Honestly" in gold ink `#8A6D0C`, scaled as one
  unit, clear space at least the shield height.
- **The standing line, on the closing frame of every concept, no exceptions:**
  `Independent student platform. Not affiliated with Curtin University.`
- **Tagline:** `Authentic anonymous reviews from Curtin students.`
- **Voice.** Second person, short declaratives, say the limitation out loud. No em
  dashes. No hype vocabulary: no revolutionise, empower, game changer, seamless,
  unleash. Australian English: enrol, organise, colour, semester, unit, never "course".
- **Call to action** is the bare domain `curtinhonestly.com` in ink or on a black band,
  never a rounded pill. If the film supports a poster or a QR campaign, use a tracked
  slug and create the referral link in the admin before anything ships.
- **Music.** The upbeat royalty-free bed already used on both films
  ("Happy Beats / Business Moves", ende.app, about 114 to 120 BPM), with key moments on
  the beat. Concept 3 is the exception and is scored differently.
- **SFX.** Keyboard ticks on type-on, a soft click on each cursor tap, a light card
  slide on each card entrance, a warm confirm on the closing lockup.

---

# Concept 1. Results are out

**What it owns:** the timing hook. Neither existing film has a reason to be watched on
any particular day. This one only makes sense in a two-week window and is built to be
posted inside it.

**Angle:** reviews get written in the fortnight after results release, late June and
late November, because that is the only moment most students are motivated to write one.
The site already knows this: `frontend/src/app/utils/results-season.util.ts` opens the
seasonal banner from 25 June to 9 July and 25 November to 9 December. The film is the
banner, given thirty seconds.

**Mood:** fast, warm, slightly conspiratorial. The busiest of the four. Cuts land on the
beat from the first frame, no slow build.

**Shootable now.** The banner is real, the catalogue is real, no review content needed.
Shoot with the system clock inside a window, or force `isResultsSeasonWindow` in a dev
build.

**Runtime: about 28s.**

| # | Time | Shot | On-screen copy | UI and cursor moments |
|---|---|---|---|---|
| 1 | 0.0 to 3.0s | Black frame. A results-style row fades up, gold rule under it, then two more stack under it fast. Deliberately unreadable specifics, blurred grades, the shape of a results page and nothing identifiable. | Over the top, Roboto Condensed, large: `Results are out.` | Never show a real transcript or a real grade. The shape carries it. Beat one lands on the first downbeat. |
| 2 | 3.0 to 6.0s | Cut to `#FAFAFA`. One question, centred. | `You just spent thirteen weeks in that unit.` then, replacing it: `Who else knows what it was actually like?` | Two lines, second replaces the first on a word stagger. Hold the second half a beat longer. |
| 3 | 6.0 to 10.5s | Push into the live site, catalogue page, top of the list. The seasonal banner is up. | Banner, verbatim: `Results are out. Which units did you just finish? Write a review while it's fresh.` | This is the hero frame. Real component, real copy. Cursor enters from the right and rests near **Write a review**, not yet clicking. |
| 4 | 10.5 to 14.0s | Cursor taps **Write a review**. The form opens. Camera holds on the top of the form. | Form heading, live: `Add a Review for [unit code]` | Click on the beat, soft click SFX, then a short push into the form. |
| 5 | 14.0 to 21.0s | The form fills, fast, one control at a time, each landing on a beat. Star rating drags to a value. Workload slider slides. Semester picks. Would-take-again toggles. Then the text field takes a short sentence. | Live labels, unchanged: `Rating (1-5 stars)` / `Workload (1-10)` / `Semester Taken` / `Would take again?` / `Your Review (min 10 characters)` | Six quick beats, roughly 1.1s each. Gold fills on the stars, gold on the slider track. Ticks on the type-on. The point is speed: the whole form is a minute of a student's life. |
| 6 | 21.0 to 24.5s | Cursor submits. The review appears on the unit page as an anonymous card with a verified badge. Camera pulls back one step. | Card byline, live: `Verified Curtin student` | Warm confirm SFX. No fake counter animation, no "+1 review" flourish. The card simply exists. |
| 7 | 24.5 to 28.0s | Clear to `#FAFAFA`. The gold shield and wordmark lockup settles centre with generous clear space, gold rule sweeps under it. | `CurtinHonestly` / `Authentic anonymous reviews from Curtin students.` / `curtinhonestly.com` / small, grey: `Independent student platform. Not affiliated with Curtin University.` | Calm resolve, music resolves and fades under the hold. |

**Closing call to action:** the bare domain over the standing line. If the post is going
out inside a results window, the caption carries the urgency, not the frame, so the film
stays reusable in both June and November.

**Posting note.** This is the only concept with an expiry. Cut it once, keep it, and
post it twice a year on the day results drop.

---

# Concept 2. Ninety seconds, then it is out of your head

**What it owns:** the review-writing flow as the entire film. The existing tour shows
reading reviews. Nothing shows writing one, which is the action the launch plan says
matters most until roughly 40 units clear three reviews.

**Angle:** the barrier to a review is not willingness, it is the suspicion that it will
take twenty minutes and need an essay. It does not. Show the whole form, end to end,
uncut in structure, with a real elapsed timer running. The film is a proof of effort.

**Mood:** brisk, procedural, satisfying. Think a well-made assembly shot. The bed is
steady rather than climbing, and the only musical lift is the submit.

**Shootable now.** The add-review form is shipped and every label below is verbatim from
`frontend/src/app/components/add-review/add-review.component.html`.

**Runtime: about 30s.**

| # | Time | Shot | On-screen copy | UI and cursor moments |
|---|---|---|---|---|
| 1 | 0.0 to 3.5s | `#FAFAFA`. One line, ink, large. A small gold timer chip sits in the top-right corner, reading `0:00`, not yet running. | `Writing a review takes about a minute.` | The timer is the film's contract with the viewer. Establish it early and never cut away from it. |
| 2 | 3.5 to 6.0s | Push to the unit page. Cursor lands on the add-review entry and taps. Form opens. The timer starts. | Live heading: `Add a Review for [unit code]` | Timer starts on the click, not before. Soft click SFX. |
| 3 | 6.0 to 9.0s | Rating. Cursor drags across the stars, gold fills left to right, the live counter beside it updates. | `Rating (1-5 stars)` and the live `x / 5` readout | Gold `#C09711` as fill on the stars. Let the drag be visible, not a snap. |
| 4 | 9.0 to 12.0s | Workload. The slider drags, the live readout tracks it. | `Workload (1-10)` and the live `x / 10` readout | The number tracking the handle in real time is the most legible micro-moment in the form. Hold on it. |
| 5 | 12.0 to 15.5s | Semester and the two toggles, three quick beats. | `Semester Taken` / `Had Exam?` / `Would take again?` | Three taps, roughly 1.1s apart, each on the beat. Gold on the active toggle state. |
| 6 | 15.5 to 18.0s | The optional fields, shown being **skipped**. Cursor passes over professor and final grade without stopping. | `Professor (Optional)` / `Final Grade (%) (Optional)` and, in grey beside them: `Optional. Skip them.` | The most persuasive beat in the film is the one where the form asks for less than expected. Do not cut it for time. |
| 7 | 18.0 to 24.0s | The text field. A short, plausible, unit-neutral sentence types in. The character minimum satisfies and the hint clears. | `Your Review (min 10 characters)`, placeholder visible first: `What did you think of this unit? Was it hard? Interesting?` Typed: two short sentences, no unit named | Keyboard ticks. Write the sentence so it names no real unit and no real teaching staff. |
| 8 | 24.0 to 27.0s | Submit. The timer stops. The card lands on the unit page, anonymous, verified badge. | Timer freezes at its real value, shown large for a beat. Below: `Anonymous. Verified. Done.` | Whatever the timer actually reads on the take is what goes on screen. Do not fake it down. |
| 9 | 27.0 to 30.0s | Clear to `#FAFAFA`. Lockup, rule, tagline, domain, standing line. | `CurtinHonestly` / `Which units did you just finish?` / `curtinhonestly.com` / `Independent student platform. Not affiliated with Curtin University.` | This film swaps the tagline for the site's own question, because the ask is the point. Keep the standing line. |

**Closing call to action:** the question plus the bare domain. This is the supply-side
film, so the ask is explicit and the promise is small.

**One thing to protect:** the timer must be genuine. If the take runs to 1:40, the film
says 1:40 and the opening line becomes "about two minutes". A timer the viewer suspects
is faked poisons the one claim the film is making.

---

# Concept 3. Anonymous, and here is exactly what that means

**What it owns:** the promise, and its limits, in the same film. Nothing in the existing
material explains what anonymity actually means here, and the launch plan names saying
the limitation out loud as the brand.

**Angle:** every platform says anonymous. Almost none says what is still stored, who can
still see it, or how you could still identify yourself by accident. CurtinHonestly's
privacy page already says all three, plainly. Putting that on screen is a stronger trust
argument than any assurance would be.

**Mood:** the quiet one. Slower cuts, more whitespace, black-and-gold rather than the
bright catalogue look. The bed is sparse and low, with the only lift on the final
lockup. No poppy beat matching anywhere.

**Shootable now.** Real account screens, real privacy page copy, no review content
needed.

**Runtime: about 32s.**

| # | Time | Shot | On-screen copy | UI and cursor moments |
|---|---|---|---|---|
| 1 | 0.0 to 4.0s | Black. One line in white, generous space around it. | `Would you write it if your name was on it?` | Full stillness. No motion at all for the first two seconds. |
| 2 | 4.0 to 7.5s | Cut to `#FAFAFA`. The register screen, real. The subtitle is the hero. | Verbatim: `Create an account to help others with your unit experiences. Reviews are always posted anonymously.` | Camera pushes in gently on the second sentence and stops. No cursor yet. |
| 3 | 7.5 to 11.5s | The verification hint, real, with the student email field beside it. | Verbatim: `Use your @student.curtin.edu.au email to register as a verified student immediately.` | Cursor enters and rests on the field without typing. Never show a real address: mask the local part. |
| 4 | 11.5 to 15.0s | The account page status badge lands, gold. | Verbatim badge: `Verified Curtin student` and, from the account page: `Verify your Curtin student email to earn a verified badge on your reviews.` | One soft confirm SFX, the only bright sound in the film so far. |
| 5 | 15.0 to 19.0s | The privacy page, real, on its anonymity heading. | Verbatim: `Your name and email are never shown next to a review. Other students see only whether the reviewer is a verified student.` | Scroll is slow and continuous, like reading rather than skimming. |
| 6 | 19.0 to 25.5s | **The honest beat.** The scroll continues into the paragraph most sites would not film. Gold rule to its left as it lands. | Verbatim: `Reviews are still linked to your account in our database.` then the rest: `That link is what lets you edit and delete your own reviews, stops the same account reviewing a unit twice, and lets us investigate reports of abuse. Site administrators can see which account wrote a review.` | Longest hold in the film. Let it be read at reading speed, roughly 0.3s per word. No music swell, no emphasis animation. The lack of production is the production. |
| 7 | 25.5 to 29.0s | One more, still verbatim, still from the privacy page. | `Be aware that the content of a review can identify you even without your name attached, for example if you mention a small class, a specific project, or your exact grade.` | Set this smaller than scene 6 and give it a hairline rule above. It is a caution, not a headline. |
| 8 | 29.0 to 32.0s | Clear. Lockup, gold rule sweep, tagline, domain, standing line. | `CurtinHonestly` / `We tell you what we store, before you ask.` / `curtinhonestly.com` / `Independent student platform. Not affiliated with Curtin University.` | The single musical lift lands here. Warm confirm on the lockup, then resolve and fade. |

**Closing call to action:** the bare domain, under the trust line rather than the
tagline. This is the one film where the tagline is the weaker close.

**Why this one is worth making even though it sells nothing.** It is the film that gets
linked in a Reddit thread when someone asks whether the site is safe to use, and that
thread is worth more than a view count. It also has the longest shelf life of the four:
it does not age with the catalogue, the review count, or the semester.

**Do not soften scene 6 in an edit pass.** Trimming it to "your reviews are linked to
your account" and cutting the administrator sentence would turn the strongest thirty
seconds in the set into ordinary reassurance.

---

# Concept 4. Two descriptions

**What it owns:** the course-selection story, told as one direct comparison rather than a
tour. It puts poster-01's idea into motion, which keeps the print and the video reading
as one campaign.

**Angle:** every unit already has a description. It was written by the people who run it.
There is a second description, written by the people who took it, and until now it only
existed in group chats.

**Mood:** starts flat and institutional, opens up on the switch. The bed is almost
absent under the handbook column and arrives properly on the cut to the reviews column.
The pacing contrast is the entire structure.

**Needs seeded review data.** This is the demand-side film, and `launch-plan.md`
sequences demand-side material to follow supply. Hold it until the single tracked number,
units with three or more reviews, is comfortably above the low forties. Shooting it early
against thin data would show the empty state at exactly the moment the film promises
answers.

**Runtime: about 30s.**

| # | Time | Shot | On-screen copy | UI and cursor moments |
|---|---|---|---|---|
| 1 | 0.0 to 3.0s | `#FAFAFA`, split down the middle by a single 1px hairline. Left column labelled, right column empty. | Left label, grey: `The description you get.` | Establish the split immediately. The empty right side is a question the film will answer. |
| 2 | 3.0 to 8.5s | The left column fills with handbook-style prose: learning outcomes, prerequisites, credit points. Set in grey `#666666`, small, dense, deliberately flat. | Generic handbook phrasing only. Do not reproduce Curtin's actual copy, and do not name a real unit in this scene. | No motion beyond the fill. No music lift. The viewer should feel the column being boring. |
| 3 | 8.5 to 11.0s | One line drops into the empty right column. | `It does not tell you the workload.` then: `Someone who took it does.` | This is the turn. The first strong beat of the track lands on the second line. |
| 4 | 11.0 to 16.0s | The right column fills with three real review cards from the live database, staggered on the beat, each with its stars, its would-take-again pill and its workload tag, all live values. | Live cards only. Each carries `Verified Curtin student`. | Card slide SFX on each entrance. Gold fill on the stars and pills. Real rows from the database at shoot time, no exceptions. |
| 5 | 16.0 to 21.0s | The hairline dissolves and the right column takes the full frame. Camera pushes into the unit page stat row, which counts up to its live values with the reviewer count beside it. | Live stats with the live denominator visible, for example the real rating over the real `n reviews` label the page already renders. | **House rule 4 applies here absolutely.** Every figure comes from the database and the reviewer count is on screen beside it. If the unit chosen at shoot time has thin data, choose a different unit rather than a different number. |
| 6 | 21.0 to 25.0s | The grade distribution histogram bars in, gold, with its live label. | The page's own label, with the live count of graded reviews | One light tick per bar. Bars grow on the beat. |
| 7 | 25.0 to 27.5s | Pull back to the catalogue, cursor scrolling, faculty chips passing. One line over it. | `Every unit. Reviewed by the students who took it.` | Short, and only as long as the catalogue can honestly carry it. If breadth is still thin at shoot time, cut this scene and give the time to scene 5. |
| 8 | 27.5 to 30.0s | Clear. Lockup, gold rule, tagline, domain, standing line. | `CurtinHonestly` / `Authentic anonymous reviews from Curtin students.` / `curtinhonestly.com` / `Independent student platform. Not affiliated with Curtin University.` | Warm confirm, resolve, fade. |

**Closing call to action:** tagline plus the bare domain. This is the demand-side film,
so the close is the promise rather than the ask.

**The pairing.** Shot this way it is the moving version of
`mockups/poster-01-two-descriptions.html`, so the poster, the lecture slide and the video
all carry the same idea. If the campaign runs both, use the same split and the same
column labels in each.

---

## Picking between them

If only one gets made now, **Concept 1** is the one I would build. It is shootable
today, it needs no review content, it asks for the thing the launch plan says matters
most, and it has a natural posting date twice a year.

**Concept 2** is the best companion to it: same supply-side ask, different objection
answered. One says "now is the moment", the other says "it costs you a minute". They cut
from the same shoot day.

**Concept 3** is the outlier and the most durable. It sells nothing, ages the slowest,
and is the one to have ready before the first Reddit thread asks whether the site is
safe.

**Concept 4** is the strongest film and the one to hold. It needs the supply that the
first three exist to create.

## Folder note

`design/marketing/README.md` carries a folder map. If this document stays here, add a
row for it, and keep the four house rules at the foot of that README as the gate: no em
dashes, gold `#C09711` is a fill and gold text on light is `#8A6D0C`, the standing line
on every printed and filmed piece, and never invent statistics about a real named unit.
