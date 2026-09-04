import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * The club portal is for club members (ROLE_CLUB) and admins. Roles normally
 * come from the stored token, which is as old as the last sign-in; when an
 * admin has granted club access since then, one call to /auth/me picks the
 * new role up so the member does not have to sign out and back in.
 */
export const clubGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  if (auth.isClubMember() || auth.isAdmin()) {
    return true;
  }
  return auth.refreshAccountStatus().pipe(
    map(() => (auth.isClubMember() || auth.isAdmin() ? true : router.createUrlTree(['/']))),
    catchError(() => of(router.createUrlTree(['/'])))
  );
};
