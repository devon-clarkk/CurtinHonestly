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
  { path: '', renderMode: RenderMode.Prerender },
  { path: '**', renderMode: RenderMode.Client },
];
