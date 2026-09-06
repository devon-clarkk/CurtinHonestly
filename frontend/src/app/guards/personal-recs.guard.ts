import { CanMatchFn } from '@angular/router';
import { environment } from '../../environments/environment';

/**
 * Personal recommendations are a build-time feature flag (PERSONAL_RECS_ENABLED,
 * see set-env.js). A disabled build never matches the For you route, so the URL
 * falls through to the not-found route and the chunk is never loaded.
 */
export const personalRecsGuard: CanMatchFn = () => environment.personalRecsEnabled;
