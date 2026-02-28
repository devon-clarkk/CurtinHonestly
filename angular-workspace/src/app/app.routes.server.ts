import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Beginners: Dynamic routes like /units/:code cannot be "Prerendered" easily
  // because the server doesn't know all the possible codes at build time.
  // We set it to 'Server' so it renders when the user requests the page.
  {
    path: 'units/:code',
    renderMode: RenderMode.Server
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender
  }
];
