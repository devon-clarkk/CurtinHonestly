import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SeoService } from './seo.service';

// Azure Static Web Apps answers every URL with no prerendered file behind it by
// rewriting to index.html and returning 200 (navigationFallback; route rules are
// documented as not applying to requests it handles). So the shell is what a
// crawler receives for /login, for /account, and for any address it invents,
// each one a 200 that looks like the home page. The shell therefore denies
// indexing by default, and the prerendered routes worth indexing opt back in.
//
// These two halves have to agree: a shell that says `index, follow` makes the
// opt-in pointless, and an opt-in that never fires makes the site invisible.
// This file covers the opt-in. The shell half is checked against the real file
// by scripts/generate-seo-assets.js, which fails the build if index.html stops
// denying by default. The Angular builder has no loader for importing the HTML
// here, and a copy of the string pasted into a test would not be the same file.

function robotsContent(doc: Document): string | null {
  return doc.querySelector('meta[name="robots"]')?.getAttribute('content') ?? null;
}

describe('robots directives', () => {
  let seo: SeoService;
  let doc: Document;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    seo = TestBed.inject(SeoService);
    doc = TestBed.inject(DOCUMENT);

    doc.querySelectorAll('meta[name="robots"]').forEach((tag) => tag.remove());
    // Seed the head the way the shipped shell does, so each case starts from
    // the state a crawler would actually land in.
    const seed = doc.createElement('meta');
    seed.setAttribute('name', 'robots');
    seed.setAttribute('content', 'noindex, nofollow');
    doc.head.appendChild(seed);
  });

  it('an indexable page overrides the shell default rather than adding a second tag', () => {
    (seo as unknown as { setIndexable(): void }).setIndexable();

    expect(doc.querySelectorAll('meta[name="robots"]')).toHaveLength(1);
    expect(robotsContent(doc)).toBe('index, follow');
  });

  it('noIndex returns a page to the shell default', () => {
    (seo as unknown as { setIndexable(): void }).setIndexable();
    seo.noIndex('Log In | CurtinHonestly');

    expect(doc.querySelectorAll('meta[name="robots"]')).toHaveLength(1);
    expect(robotsContent(doc)).toBe('noindex, nofollow');
  });
});
