# Spike: pregenerated "X vs Y" comparison pages

Analysis only, no implementation. Question asked: from an SEO perspective, should we
pregenerate unit-versus-unit pages for common searches?

## Short answer

The mechanism is easy, and it is the wrong thing to start with. Combinatorial auto-generated
pages are the textbook shape of a doorway-page penalty, and the search demand for
"COMP1000 vs COMP1005" is almost certainly close to zero. Recommendation is a small,
hard-gated, manually-seeded set (20 to 50 pages) treated as an experiment with a kill
switch, not a programmatic sweep.

## Why the obvious version fails

**Scale.** 133 units in the catalogue today gives 133 x 132 / 2 = **8,778** ordered-insensitive
pairs. The catalogue is meant to grow to the full Curtin handbook, which is several thousand
units. At 2,000 units that is **~2 million** pairs. There is no version of "generate all pairs"
that survives contact with a crawler.

**Thin content.** A generated versus page is two existing unit records in a table. Nearly all of
its text already exists on the two unit pages it draws from. Google's guidance on doorway pages
and scaled content abuse targets exactly this: large numbers of pages generated primarily for
search engines that add little value over the pages they aggregate. The risk is not that the
versus pages fail to rank; it is that they drag down the unit pages that currently do.

**No demand signal.** Nobody has checked whether anyone searches this. "unit A vs unit B" is a
established pattern for consumer products with a purchase decision. Curtin students choosing
electives are a small population, and the phrasing they use is more likely "is COMP1000 hard"
or "easiest electives Curtin". Those are different pages and probably a better bet.

**Data gate.** Most units currently have 8 reviews and prod has none at all. A comparison
between two units with 8 reviews each is not a useful page, and publishing hundreds of them
signals low quality across the site.

## What the pipeline would need

Current SEO build, in order (`frontend/package.json` build script):

1. `set-env.js` writes `environment.ts` with `apiUrl`/`siteUrl`/`seoEnabled`
2. `scripts/fetch-unit-codes.js` pulls unit codes from the live API into `src/generated/`
3. `scripts/generate-seo-assets.js` writes `public/robots.txt` and `public/sitemap.xml` from
   `src/generated/unit-sitemap-meta.json`
4. `ng build` with `outputMode: "server"` and SSR prerenders routes

Adding versus pages means:

- A real route, e.g. `/compare/:codeA-vs-:codeB`, distinct from the current `?units=` query form.
  The query form must stay `noIndex` (it already is) so the two do not compete.
- A pair-selection step producing the list of pairs to build, written to `src/generated/`.
- `generate-seo-assets.js` extended to emit those URLs into the sitemap.
- Prerendering each pair at build time. This is the cost driver: build time scales linearly with
  pair count, and each page needs its unit data fetched at build.
- Per-page canonical, title, description, and ideally `ItemList`/`Course` JSON-LD reusing
  `unit-seo.utils.ts`.
- Ordering must be canonicalised (`A-vs-B` and `B-vs-A` are the same page) or every pair
  becomes a duplicate-content pair.

## If it is done anyway

Gate hard, and make each page earn its place:

- **Eligibility:** both units have >= 15 reviews, sit in the same faculty, and are the same level.
  On today's data that yields almost nothing, which is itself the answer about timing.
- **Cap:** hard limit of 50 pages, sorted by combined review count. Log what was dropped.
- **Substance:** the page must contain something neither unit page has. The honest candidates are
  a stated difference in workload distribution, grade distribution, and would-take-again, with a
  sentence generated from the actual numbers. If the page is only a table, do not ship it.
- **Canonical ordering:** sort the two codes alphabetically to build the slug.
- **Measurement:** ship with the pages in the sitemap, then check Search Console impressions after
  6 to 8 weeks. If impressions are negligible, remove them rather than leaving them to rot.

## What is probably a better use of the same effort

- Unit pages already prerender and already carry `Course` JSON-LD. Getting more reviews onto them
  raises the quality of pages that already have demand.
- Prod has 133 units and **zero reviews**. Search traffic to a review site with no reviews will not
  convert regardless of how many pages exist.
- Question-shaped pages ("is X hard", "X workload") map to how students actually search and can be
  built from the same data without the combinatorial explosion.

## Decision needed

Not whether it can be built. Whether there is demand worth building for, and whether the site has
enough review data that a comparison page would be worth landing on. Both currently point to
"not yet".
