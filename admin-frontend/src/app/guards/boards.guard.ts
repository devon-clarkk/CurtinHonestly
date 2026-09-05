import { CanMatchFn } from '@angular/router';
import { environment } from '../../environments/environment';

/**
 * Board moderation is a build-time feature flag (BOARDS_ENABLED, see set-env.js).
 * As a canMatch guard, a disabled build never matches the boards route, so the
 * URL falls through to the not-found handling instead.
 */
export const boardsGuard: CanMatchFn = () => environment.boardsEnabled;
