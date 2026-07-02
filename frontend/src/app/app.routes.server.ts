import { PrerenderFallback, RenderMode, ServerRoute } from '@angular/ssr';
import unitCodes from '../generated/unit-codes.json';

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
  { path: '', renderMode: RenderMode.Prerender },
  { path: '**', renderMode: RenderMode.Client },
];
