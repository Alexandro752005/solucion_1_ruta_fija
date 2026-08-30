import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import {
  AUTH_RETRY_ATTEMPTED,
  SKIP_AUTH_REFRESH,
} from './auth-http-context';
import { AuthSessionService } from './auth-session.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const runtimeConfig = inject(RuntimeConfigService);

  if (!runtimeConfig.isApiUrl(request.url)) {
    return next(request);
  }

  const session = inject(AuthSessionService);
  const router = inject(Router);
  const cookieSessionEndpoint = isCookieSessionEndpoint(request.url);
  const token = session.accessToken();
  const authenticatedRequest = request.clone({
    withCredentials: true,
    setHeaders:
      !cookieSessionEndpoint &&
      token !== null &&
      !request.headers.has('Authorization')
        ? { Authorization: `Bearer ${token}` }
        : {},
  });

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      const mustTryRefresh =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !request.context.get(SKIP_AUTH_REFRESH) &&
        !request.context.get(AUTH_RETRY_ATTEMPTED) &&
        !cookieSessionEndpoint;

      if (!mustTryRefresh) {
        return throwError(() => error);
      }

      return session.refreshAccessToken().pipe(
        catchError(() => {
          session.clearSession();
          const returnUrl = router.url.startsWith('/login')
            ? '/dashboard'
            : router.url;
          void router.navigate(['/login'], {
            queryParams: { reason: 'session-expired', returnUrl },
          });
          return throwError(() => error);
        }),
        switchMap((refreshedToken) =>
          next(
            request.clone({
              withCredentials: true,
              context: request.context.set(AUTH_RETRY_ATTEMPTED, true),
              setHeaders: { Authorization: `Bearer ${refreshedToken}` },
            }),
          ),
        ),
      );
    }),
  );
};

function isCookieSessionEndpoint(url: string): boolean {
  return /\/auth\/(login|refresh|logout)(?:[/?#]|$)/.test(url);
}
