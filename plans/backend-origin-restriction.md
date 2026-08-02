# Restricting who can call the backend

Question asked: is there an Azure CORS policy that treats `app.domain` and `www.domain` as the
same origin, so we can block all other requests to the backend?

## The two parts of the question

**The Azure feature is real.** Azure Container Apps supports a CORS policy at the ingress layer,
configured under Networking > CORS in the portal or with
`az containerapp ingress cors update --yaml <file>`. It sets `allowedOrigins`, `allowedMethods`,
`allowedHeaders`, `exposeHeaders`, `allowCredentials`, and `maxAge`.

**But `allowedOrigins` is an exact-match list.** The documented format is a list of full origins
such as `["https://example.com"]`, with `*` to allow everything. There is no wildcard-subdomain
syntax. "Treat `app.domain` and `www.domain` as the same" is not a feature; you list both
explicitly. Which is exactly what `app.cors.allowed-origins` in `application.yml` already does.

**And CORS cannot block anything.** This is the important part. CORS is enforced by browsers, not
by the server. The policy controls which `Access-Control-Allow-Origin` header comes back, and a
browser then refuses to let page JavaScript from a disallowed origin *read* the response. The
request still reaches the backend and is still processed. curl, Postman, a Python script, a
scraper, or any server-side caller ignores CORS entirely.

So enabling ingress CORS would not stop anyone calling the API. It would only change which
browser-based front-ends can use it.

## Do not enable it on top of the Spring config

The backend already sets CORS headers from `app.cors.allowed-origins`, verified working against
the dev SWA origin. If the ingress also injects CORS headers, responses can carry **two**
`Access-Control-Allow-Origin` headers, which browsers reject outright. That would break the site
in a way that looks like a CORS misconfiguration but is actually a duplication.

Keeping it in Spring is also better operationally: the allowed origins live in `application.yml`
under version control and deploy with the app, whereas ingress CORS is portal/CLI state that
exists nowhere in the repo.

**Recommendation: leave CORS where it is.**

## What actually restricts access

If the goal is genuinely to stop non-browser callers:

- **IP ingress restrictions** (`az containerapp ingress access-restriction`). Allow or deny rules
  on address ranges. Impractical for a public consumer site since visitors come from everywhere.
  Useful only for locking the backend to a known front door.
- **Azure Front Door in front, with the Container App restricted to Front Door.** Gives a WAF, bot
  rules, and rate limiting at the edge. This is the real answer if abuse becomes a problem, and it
  is a meaningful amount of setup.
- **Authentication on the endpoints.** The endpoints that matter (posting reviews, likes, reports)
  already require a JWT. `GET /units` is deliberately public.

Gotcha worth knowing: IP restrictions block at the network layer, including CORS preflight
`OPTIONS` requests. When they are misconfigured the browser reports a CORS error, which sends you
looking in the wrong place.

## The honest framing

`GET /units` is a public read endpoint on a public site whose entire purpose is being read. It
cannot be meaningfully hidden from non-browser clients while remaining usable by the front-end.
The realistic protections are the ones already in place or cheap to add:

- `RateLimitFilter` already caps the sensitive endpoints. Broadening it is cheaper than any
  network-layer work.
- Writes require auth.
- If scraping becomes a real problem, Front Door with bot rules is the tool, not CORS.

## Sources

- [Configure CORS in the Azure portal for Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/cors)
- [az containerapp ingress cors](https://learn.microsoft.com/en-us/cli/azure/containerapp/ingress/cors?view=azure-cli-latest)
- [Set up IP ingress restrictions in Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/ip-restrictions)
- [Ingress in Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/ingress-overview)
