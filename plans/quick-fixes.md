# Quick Fixes

Small bugs and rough edges found while reading the code. Each is independently
shippable.

---

## 1. 0-star ratings will break review snippets — fix BEFORE the SEO branch merges

**Done** — minimum is now 1 star: `ReviewCreateRequest.rating` is `@Min(1)`, the add-review slider is
`min="1"`. `unit-seo.utils.ts` already declared `worstRating: '1'`, so no change was needed there.

The add-review slider (`min="0"`) and backend validation
(`ReviewService.createReview`: `rating < 0 || rating > 5`) both allow `rating = 0`,
but the SEO plan's JSON-LD declares `worstRating: 1`. One 0-star review emits an
out-of-bounds `ratingValue` and can invalidate rich results for that unit page.

**Decide:** make 1 the minimum (probably right — what does 0 stars mean vs 1?) or
change the schema bounds to 0. Either way, align form, backend validation, and
`unit-seo.utils.ts` in the same PR.

## 2. Unit detail page has no error state

`unit-detail.component.ts` never handles errors on `unit$` — a bad or removed unit
code leaves the user on the "Loading unit details..." spinner forever, and there is no
404 route. Add an error branch (`catchError` → error template with a link back to the
catalog) and a wildcard route.

## 3. Validation errors return HTTP 500

`ReviewResource.createReview` catches all exceptions and returns
`internalServerError()` — so rating-out-of-range, profanity, and missing-unit
validation failures are 500s instead of 400s. Cosmetic for users (frontend shows the
message either way) but poisons error monitoring. Split `IllegalArgumentException`
→ 400. Same pattern exists in `UnitResource.getUnits` (including a
`NullPointerException` special case that leaks stack frames into the response body).

(Overlaps [cyber-audit.md](cyber-audit.md) #9 — raw exception messages concatenated
into hand-built JSON. Fix both at once with a `@ControllerAdvice` global handler.)

## 4. Hardcoded semester dropdown will go stale

`add-review.component.html` offers a fixed list topping out at "Semester 1, 2026".
Generate options from the current date (current + previous ~4 semesters).

## 5. Profanity word list ships in the client bundle

`profanity-list.ts` (`BANNED_WORDS`) is imported by `add-review.component.ts` — bundle
weight plus a public list of exactly which words to leetspeak around. Backend
`ProfanityFilterService` already enforces it. Drop the client-side check or reduce it
to a generic friendly pre-check message driven by the server response.

## 6. Reviews render dangling optional fields

`unit-detail.component.html` review cards:

- `• Prof. ` renders with nothing after it when `professor` is empty
- `Grade: %` renders when `finalGrade` is null

Conditionally render both. (Surfacing *more* of the collected data — `hasExam`, review
dates — is the feature version of this, tracked in [review-experience.md](review-experience.md) #1.)

## 7. Reviews are unordered

**Done** — `ReviewService.getReviewsByUnitCode` now uses
`ReviewRepo.findByUnit_IdOrderByCreatedAtDesc`, so `GET /units/{code}/reviews` returns newest first.

`ReviewService.getReviewsByUnitCode` → `findByUnit_Id` with no `ORDER BY`. Display
order is arbitrary, and the SEO JSON-LD takes "first 10 in API order". Order
`createdAt DESC` server-side.

## 8. Likely-unused review listing endpoint

`GET /units/{unitCode}/reviews` (in `UnitResource`) appears unused by the frontend —
unit detail gets reviews via `UnitDetailsDTO`. Remove it (or consciously keep + test
it) rather than maintaining a second review-listing path.

## 9. `MyReviewDTO` is too thin for the my-reviews page

Lacks `workload`, `finalGrade`, `professor`, `hasExam`, `wouldTakeAgain` — users can't
see their own full review, which becomes a blocker once review editing exists. Widen
alongside the edit feature (see [review-experience.md](review-experience.md)).

## 10. Zero-review catalog cards look dead

Superseded — the fix ("No reviews yet — be the first" card state) is specified and
scheduled in [frontend-revamp-plan.md](frontend-revamp-plan.md) Phase 2 (audit item A1).

## 11. Weak email format validation on register

`AuthController.isValidEmail` is `contains("@") && contains(".")`. Low stakes, but a
standard regex or Jakarta `@Email` validation is nearly free — matters slightly more
once emails are actually sent (verification/reset flows). (Also flagged as finding #17
in [cyber-audit.md](cyber-audit.md).)
