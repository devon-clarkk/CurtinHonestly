# SEO tooling

Two tools, and they do different jobs. Run both.

- **OpenSEO** is the data. Keyword volumes, SERP positions, competitor gaps,
  backlinks, rank tracking. It is a service you host, and it exposes an MCP
  server so Claude Code can query that data directly.
- **claude-seo** is the method. Skills and agents that walk an audit through
  technical SEO, schema, E-E-A-T, and GEO. It reads this repository and the live
  site; it needs no account and no key.

They compose: claude-seo decides what to look at, OpenSEO says what the numbers
are. Either works on its own.

---

## OpenSEO

Upstream: <https://github.com/every-app/open-seo>. Self-host it over Docker.

**It needs a DataForSEO key, and that key costs money.** DataForSEO is
pay-as-you-go, and every keyword, SERP, and backlink call bills against it. The
technical and on-page half of an audit needs nothing; keyword and competitor
research is the part that bills.

```bash
git clone --depth 1 https://github.com/every-app/open-seo.git
```

```bash
cd open-seo && cp .env.example .env
```

Put your DataForSEO key in `.env` as `DATAFORSEO_API_KEY`, base64-encoded from
`email:password` (see the upstream `docs/DATAFORSEO_API_KEY.md`). `OPENROUTER_API_KEY`
is optional and only powers SAM, the in-app agent. Claude Code does that job here.

```bash
docker compose up -d
```

First start takes a minute or two. The app is then at <http://localhost:3001>.

**Do not put this on a public address.** The Docker configuration runs
`AUTH_MODE=local_noauth`: no authentication at all, with a standing admin user.
Upstream's own compose file binds to `127.0.0.1` for exactly this reason. If you
ever need it reachable from elsewhere, put it behind your own authenticating
proxy or tunnel rather than opening the port.

### The MCP wiring

[`.mcp.json`](../.mcp.json) at the repository root points Claude Code at the
local instance, so the tools appear whenever you work in this repo and the
container is running. Approve the server when Claude Code prompts on first use.

The hosted service documents its MCP endpoint as `/mcp`, and the self-hosted
container runs the same application. Confirm the path against your instance
before assuming a silent failure is something else:

```bash
curl -i http://localhost:3001/mcp
```

If it 404s, check the running app's own docs at <http://localhost:3001> and
update the URL in `.mcp.json`. `docker compose logs -f` and
<http://localhost:3001/api/health> cover the rest.

---

## claude-seo

Upstream: <https://github.com/AgriciDaniel/claude-seo> (MIT). Needs Python 3.10+
and Git. No API key.

Install it from a Claude Code session. `/plugin` is an interactive command, so
it has to be you rather than an agent:

```
/plugin marketplace add AgriciDaniel/claude-seo
```

```
/plugin install claude-seo@agricidaniel-claude-seo
```

Then `/seo setup` to build its virtual environment, and `/seo doctor` to check it.

There is also a manual `install.ps1`. It git-clones the repo to a temp directory
and copies the skills and agents into `%USERPROFILE%\.claude\skills\` and
`%USERPROFILE%\.claude\agents\`, then has you run `claude-seo setup` to create
the Python venv and fetch Playwright's Chromium. Two things to know before
choosing it over the plugin: it installs at **user** scope, so the skills load in
every project, not just this one; and it is third-party code running on your
machine, so read the script first. The plugin route keeps its files in Claude's
own plugin data and is easier to remove.

---

## Auditing this site

The site is Angular with prerendering, deployed to Azure Static Web Apps. Two
things shape every finding:

- `/`, `/faculty/:slug` and `/units/:code` are prerendered. Everything else
  reaches a crawler as a bare shell, and that shell is deliberately `noindex`.
- Unmatched URLs are served by `navigationFallback` with a **200**, not a 404.
  See [`AUDIT-2026-08-31.md`](AUDIT-2026-08-31.md) for why that matters and what
  was done about it.
- 1,761 units are indexable and 32 of them have a review. That ratio shapes
  almost every recommendation worth making, so read the audit's first ranked item
  before acting on tool output.

Point tooling at `https://www.curtinhonestly.com`: the apex 301s to `www`, and
`www` is what canonicals and the sitemap declare.

Useful starting prompts once both tools are installed:

- "Run a technical SEO audit of https://www.curtinhonestly.com and prioritise by
  impact." (claude-seo, no key needed)
- "What do students search for when choosing university units? Pull volumes for
  Curtin unit-review terms." (OpenSEO, bills DataForSEO)
- "Which curtinhonestly.com pages are close to ranking?" (OpenSEO + Google Search
  Console; needs the GSC integration configured)

The current findings and the ranked plan live in
[`AUDIT-2026-08-31.md`](AUDIT-2026-08-31.md).
