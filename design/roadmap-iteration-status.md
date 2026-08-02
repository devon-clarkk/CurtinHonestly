# Roadmap iteration — status report

Covers the full pass through `plans/roadmap.md`: 15 PRs (#61–#75), all stacked
sequentially off `dev`, all still open (none merged — user merges via GitHub UI).
`origin/dev` was re-verified unchanged before every single branch in the stack
was cut; it never moved during this iteration.

## Merge order

Branches were stacked in this exact order — merge them in this order too, so
each PR's diff against its base stays clean:

```
dev
 └─ #61 feat/password-reset               (1.2 password reset)
     └─ #62 feat/rate-limit-broaden        (1.3 rate limiting)
         └─ #63 feat/anonymize-on-delete   (1.4 anonymize-on-delete)
             └─ #64 feat/surface-review-data   (2.1 exam/date/staleness signals)
                 └─ #65 feat/quick-fix-cleanup (2.3 quick-fix bundle)
                     └─ #66 feat/unit-tips          (2.2 unit tips)
                         └─ #67 feat/review-editing     (2.4 review editing)
                             └─ #68 feat/request-a-unit     (3.1 request-a-unit)
                                 └─ #69 feat/seasonal-review-prompt (3.2 seasonal prompt)
                                     └─ #70 feat/unit-comparison        (3.3 compare units)
                                         └─ #71 feat/lecturer-filter        (4.2 lecturer filter)
                                             └─ #72 feat/report-review          (4.3 report/flag)
                                                 └─ #73 feat/grade-histogram        (4.5 grade histogram)
                                                     └─ #74 feat/assessment-tags        (4.1 assessment tags)
                                                         └─ #75 feat/prerequisite-checker   (4.4 prerequisite checker)
```

Each PR's GitHub diff view will show every commit from earlier branches in
the stack until the ones below it are merged first — that's expected for a
stacked-branch workflow, not a conflict. Once #61 merges into `dev`, retarget
#62's base to `dev` (GitHub does this automatically) and so on down the
chain.

## What shipped, by roadmap phase

**Phase 1 — Trust & auth**
- 1.2 Password reset via emailed link (#61) — see
  [design/1.2-password-reset-status.md](1.2-password-reset-status.md) for the
  detailed write-up, including the one known limitation.
- 1.3 Rate limiting broadened to login/register/reviews (#62).
- 1.4 Anonymize-instead-of-delete on account deletion (#63).

**Phase 2 — Review quality**
- 2.1 Surface `hasExam`, review dates, and staleness on the unit page (#64).
- 2.3 Quick-fix cleanup: semester dropdown, profanity list, 404 state (#65).
- 2.2 Unit tips — lightweight one-liners distinct from full reviews (#66).
- 2.4 Review editing, `PUT /reviews/{id}` (#67).

**Phase 3 — Catalog & growth**
- 3.1 Request-a-unit empty-state capture (#68).
- 3.2 End-of-semester review-prompt banner (#69).
- 3.3 Side-by-side unit comparison (#70).

**Phase 4 — Review experience**
- 4.2 Lecturer-aware review filtering (#71).
- 4.3 Report/flag button with an admin moderation queue (#72).
- 4.5 Grade distribution histogram, gated at n≥5 reviews (#73).
- 4.1 Assessment/experience tags (#74).
- 4.4 Prerequisite eligibility checker (#75).

Every PR includes, in its own description: what was implemented, the exact
compile/test/lint commands run, and a step-by-step account of browser
verification (including what was mocked and why, given this sandbox has no
live backend). This report doesn't repeat that detail — it's the roll-up.

## Bugs found and fixed during this iteration

Two real bugs were caught by the browser-verification step (not by
compilation or unit tests) and fixed before the relevant PR was raised:

1. **NG0103 infinite change detection (4.2, `unit-detail.component.ts`).**
   `lecturerSummary()` and `filteredReviews()` were called directly from the
   template with `unit.reviews`. Without memoization, each call allocated a
   fresh array, and returning a new reference every change-detection pass
   never let Angular's view stabilize. Fixed by caching on array-reference
   equality (`{reviews, result}` cache, recomputed only when the `reviews`
   array identity changes). The same pattern was applied proactively to
   `gradeDistribution()` in 4.5 to avoid repeating the bug.

2. **Compare-component empty-table edge case (3.3, `compare.component.ts`).**
   When 2 unit codes were requested but *all* fetches failed, the original
   code only set an error message if `codes.length < 2` — with 2 codes
   requested that condition was false, so it silently rendered an empty
   table (headers with no data columns) instead of an error state. Fixed by
   checking `loaded.length < 2` (successfully loaded units) rather than
   `codes.length < 2` (units requested).

Both were confirmed fixed by re-running the same failure scenario against a
fresh preview server after the fix.

## Explicitly out of scope (with rationale)

- **3.4 Catalog coverage pipeline** — a data-acquisition project (scraping/
  importing the full Curtin handbook), not a code change to an existing
  feature. Different shape of work from everything else in this pass.
- **Phase 5 (data-gated bets)** — items like the "State of Curtin Units"
  report and the workload-vs-rating scatter view are explicitly gated on
  review volume the site doesn't have yet (see `plans/big-features.md` #5–6).
  Building them now would ship empty/unconvincing pages.
- **Frontend revamp** (`plans/frontend-revamp-plan.md`) — a separate visual
  redesign track, orthogonal to feature work; mixing it into this pass would
  have made every PR's diff harder to review.
- **Monetization** (`plans/monetization.md`) — business-model work, not
  feature work; needs a product decision from the user before any code.

These weren't skipped by oversight — each has a standing plan doc describing
what it would take, so they're ready to pick up whenever they're prioritized.

## Known limitations carried forward

- **Password reset (1.2) doesn't revoke existing JWTs.** A stolen JWT issued
  before a password reset remains valid until it naturally expires. Logged
  in [design/1.2-password-reset-status.md](1.2-password-reset-status.md) at
  the time; still true, not addressed in this pass.
- **4.4 prerequisite checker can't verify course-level requirements**
  (`CoursePrerequisiteOption` — e.g. "24 credits in Engineering"). Only
  unit-code-level options are checked against a student's completed-units
  list; groups that depend on a course-level option are marked
  "can't verify" rather than guessed at. Closing this gap would need course
  credit-progress data the model doesn't have (see `plans/big-features.md`
  #8, "Degree/major-relevant browsing" — same underlying data gap).
- **`ng test` has 2 pre-existing failures** in `src/app/app.spec.ts`
  (missing `ActivatedRoute` provider), present before this iteration started
  (confirmed via `git stash` comparison) and unrelated to every PR raised —
  not fixed, since it's outside the scope of any specific roadmap item.

## Testing approach used across all 15 PRs

- Backend: `./gradlew compileJava compileTestJava` on every PR, plus new
  Mockito-only unit tests per feature (`@SpringBootTest` suites remain
  blocked by a pre-existing Testcontainers/Docker issue in this sandbox,
  unrelated to any of these changes).
- Frontend: `npx tsc -p tsconfig.app.json --noEmit` and
  `npx ng test --watch=false` on every PR; new `vitest` specs for pure
  utility functions where applicable.
- Browser verification via the Claude Preview MCP tools on a **fresh**
  preview server per feature (a stale long-running server was found to leak
  mocked state between rounds of testing during 4.2 — using a fresh server
  each time avoids false positives/negatives), using `window.ng` to mock
  component/service state directly since this sandbox has no live backend.
  Real DOM clicks were used wherever they'd fire correctly; a few components
  with heavily-mocked observable state needed the component method invoked
  directly via `ng.getComponent(el).method(...)` instead, with the resulting
  network request inspected separately to confirm correctness.
