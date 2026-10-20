# Copy review changes

Editorial pass over everything in `design/marketing/`, 14 August 2026. Every claim in
the copy was cross-checked against the repo before it was kept or changed. The affected
exports were regenerated, so `exports/` matches `mockups/` again.

## Accuracy fixes (the important ones)

1. **The one-sentence pitch is not verbatim.** `launch-plan.md` claimed the pitch was
   taken word for word from `share-copy.txt`. The real share copy has no "grades" and
   reads slightly differently. The provenance note now quotes the actual source and
   says the only change is adding grades, because the site shows them.
2. **"Honest reviews. Better choices." is not site copy.** It is the launch video's
   closing line (`design/brag-launch-video/`). The voice-rules section now attributes
   it correctly.
3. **The empty-state quote was not verbatim.** The live site says "No reviews yet. Be
   the first to write one!" with an exclamation mark
   (`unit-detail.component.html`). Poster 02 and the plan quoted it without one while
   claiming an exact lift. Both now match the site exactly. On a poster whose whole
   argument is being product-true, the quote has to be a real quote.
4. **Lecturer FAQ overclaimed.** "Lecturers have no access to it at all" was wrong,
   since the site is public and anyone can read it. The answer now says a lecturer can
   read reviews like anyone else, but no name ever appears and only administrators can
   see authorship. An overclaim in the anonymity FAQ is the exact failure the plan
   itself warns against.
5. **Referral-table landing page corrected.** The table said `flyer-a5-stall` lands on
   Register, but the generated QR encodes the home URL. I decoded all six QR PNGs to
   check: every one carries `https://www.curtinhonestly.com/?ref=<slug>` with the right
   slug. The table row now says Home, and the URL-format note explains that a different
   landing page just means encoding that path in the QR, since the app captures
   `?ref=` on any page.
6. **README QR example used the wrong host.** The regeneration snippet encoded the bare
   apex while the plan (and the actual assets) use the `www` form. The example now uses
   `www` and the section says why: tracking should not depend on the apex redirect.

## Clarity and voice fixes

- **Poster 03 body:** "nobody at Curtin can take anything off it because it reads
  badly" could be parsed as the site reading badly. Now: "nobody at Curtin can take a
  review down because it makes a unit look bad." Same argument, one meaning.
- **Lecture slide lead:** "without knowing any of what you just learned" tightened to
  "knowing none of what you just learned the hard way."
- **V3 vox pop:** "offer to shoot faces off" had an unfortunate second reading. Now
  "offer to keep faces out of frame."
- **Founder Reddit post:** "It is not affiliated" contracted to "It's not affiliated"
  to match the contractions in the rest of the post. Reddit copy lives or dies on
  sounding like a person.
- **Gallery blurb for poster 02:** "which is why it converts" stated a prediction as a
  fact. Replaced with the actual reasoning: asking someone to fix a visible problem
  beats asking them to join something.
- Small grammar fixes: a stray parenthesis in the Post C heading, "Expect one or two
  yes" is now "Expect one or two to say yes", and the WA lottery sentence now reads
  "whether the game is one of chance or skill".

## Australian English

The copy was already almost fully AU (enrol, catalogue, organise, dramatise). One miss:
"exported artifact" in section 10, now reworded to "exported PNGs", which is both AU
and more precise.

## Em dashes

None found in any file in this folder, and none introduced. The house rule holds.

## STE (ASD-STE100), where it was and was not applied

Applied to explanatory and instructional copy where it removed a second reading:
the poster 03 body line, the lecturer FAQ answer, the URL-format paragraph (split into
shorter single-purpose sentences), and the README QR guidance.

Deliberately **not** applied to:

- Headlines and payoff lines ("Same unit. Two descriptions.", "Students tell you what
  it costs you.", "That is the point."). Fragments and rhetorical structure are the
  point; STE would flatten them into manual-speak.
- The passive in poster 01's subhead ("One was written to fill a page in the
  handbook. The other was written by someone who had to sit the exam."). The parallel
  passive is the joke's engine.
- The Reddit and modmail drafts. They must read as a person typing, so contractions,
  asides, and mild idiom stay.
- Idioms like "falls off a cliff" and "the money mostly buys speed" in the plan body.
  They are Devon-voiced and unambiguous in context.

## Exports

Regenerated after the copy edits, from the mockups via headless Chrome per the README:
`poster-02-zero-reviews` (PDF and PNG), `poster-03-not-affiliated` (PDF and PNG), and
`slide-lecture-16x9.png`. Poster 01 was untouched, so its exports were left alone. The
regenerated poster 02 PNG was visually checked: fonts load, layout holds, and the
corrected empty-state line renders.

## Left alone on purpose

- The tagline capitalisation difference against the brag videos ("Curtin Students"
  there, "Curtin students" here). Lowercase is correct; the videos are out of scope.
- The covert Reddit drafts in 6.6. They stay clearly labelled and argued against,
  which is the structure Devon asked for. No copy changes needed.
- The overall structure, arguments, and nearly all of the plan's prose. It already
  follows the voice rules it sets out, so this pass fixed facts and sharpened lines
  rather than rewriting.
