import { CanMatchFn } from '@angular/router';
import { environment } from '../../environments/environment';

/**
 * Community boards are a build-time feature flag (BOARDS_ENABLED, see set-env.js).
 * As a canMatch guard, a disabled build never matches the boards routes, so the
 * URL falls through to the not-found route and the boards chunk is never loaded.
 */
export const boardsGuard: CanMatchFn = () => environment.boardsEnabled;
