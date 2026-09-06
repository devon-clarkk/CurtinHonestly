import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Only add the token if we are in a browser environment (localStorage exists)
  if (typeof localStorage !== 'undefined') {
    const token = localStorage.getItem('auth_token');

    // If a token exists, clone the request and add the Authorization header
    if (token) {
      const auth = inject(AuthService);
      const authReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
      // The server rejecting the stored token (expired, revoked after a password
      // change, or signed with a rotated secret) means there is no session. End
      // it locally so the header flips to signed out and guards send the visitor
      // to the login page, then let the caller handle the failed request.
      return next(authReq).pipe(
        catchError((err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 401 && !req.url.endsWith('/auth/login')) {
            auth.sessionRejected();
          }
          return throwError(() => err);
        })
      );
    }
  }

  // If no token or not in browser, just continue with the original request
  return next(req);
};
