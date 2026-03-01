import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Only add the token if we are in a browser environment (localStorage exists)
  if (typeof localStorage !== 'undefined') {
    const token = localStorage.getItem('auth_token');
    
    // If a token exists, clone the request and add the Authorization header
    if (token) {
      const authReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
      return next(authReq);
    }
  }
  
  // If no token or not in browser, just continue with the original request
  return next(req);
};
