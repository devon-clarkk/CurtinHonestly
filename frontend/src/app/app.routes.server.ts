import { PrerenderFallback, RenderMode, ServerRoute } from '@angular/ssr';
import unitCodes from '../generated/unit-codes.json';
import { FACULTY_HUBS } from './utils/faculty.util';

export const serverRoutes: ServerRoute[] = [
  { path: 'login', renderMode: RenderMode.Client },
  { path: 'register', renderMode: RenderMode.Client },
  {
    path: 'units/:code',
    renderMode: RenderMode.Prerender,
    async getPrerenderParams() {
      return unitCodes.map((code) => ({ code }));
    },
    fallback: PrerenderFallback.Client,
  },
  // The five faculty hubs. They are the only pages that link the whole
  // catalogue, so they have to reach a crawler as finished HTML rather than as
  // a shell that has to run JavaScript to grow several hundred links.
  {
    path: 'faculty/:slug',
    renderMode: RenderMode.Prerender,
    async getPrerenderParams() {
      return FACULTY_HUBS.map((hub) => ({ slug: hub.slug }));
    },
    fallback: PrerenderFallback.Client,
  },
  // Terms and Privacy are static copy, and they are what anyone assessing
  // whether to trust this site goes looking for. Client rendering left them
  // unreadable to any fetcher that does not run JavaScript, which is most
  // automated readers: the shell they received carried 148 words of header and
  // footer and none of the policy. Prerendering costs two pages and makes the
  // answer available to whoever asks.
  // Who runs the site and how to reach them. Indexed, so unlike terms and
  // privacy these have to arrive as finished HTML rather than merely be
  // readable once fetched.
  { path: 'about', renderMode: RenderMode.Prerender },
  { path: 'contact', renderMode: RenderMode.Prerender },
  { path: 'terms', renderMode: RenderMode.Prerender },
  { path: 'privacy', renderMode: RenderMode.Prerender },
  { path: '', renderMode: RenderMode.Prerender },
  { path: '**', renderMode: RenderMode.Client },
];
