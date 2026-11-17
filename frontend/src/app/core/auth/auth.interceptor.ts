import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, map, switchMap, throwError } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import {
  AUTH_RETRY_ATTEMPTED,
  SKIP_AUTH_REFRESH,
} from './auth-http-context';
import { AuthSessionService, SupersededAuthOperationError } from './auth-session.service';
import { crmSessionContextKey } from './auth-session-context';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const runtimeConfig = inject(RuntimeConfigService);

  if (!runtimeConfig.isApiUrl(request.url)) {
    return next(request);
  }

  const session = inject(AuthSessionService);
  const router = inject(Router);
  const cookieSessionEndpoint = isCookieSessionEndpoint(request.url);
  const originalLifecycle = session.lifecycleVersion();
  const originalContext = cookieSessionEndpoint ? null : crmSessionContextKey(session.user());
  const contextChanged = () => originalLifecycle !== session.lifecycleVersion() ||
    (originalContext !== null && originalContext !== crmSessionContextKey(session.user()));
  const changedContextError = () => new HttpErrorResponse({ status: 409, statusText: 'Session context changed', url: request.url,
    error: { code: 'SESSION_CONTEXT_CHANGED', message: 'La sesión cambió. Consulta el estado desde el contexto actual; no se repetirá la solicitud anterior.' } });
  const retainCurrentContext = <T>(event: T): T => {
    if (contextChanged()) throw changedContextError();
    return event;
  };
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
    map(retainCurrentContext),
    catchError((error: unknown) => {
      if (contextChanged()) return throwError(changedContextError);
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
        catchError((refreshError: unknown) => {
          if (refreshError instanceof SupersededAuthOperationError ||
              (contextChanged() && session.isAuthenticated())) {
            return throwError(changedContextError);
          }
          session.clearSession();
          const returnUrl = router.url.startsWith('/login')
            ? '/dashboard'
            : router.url;
          void router.navigate(['/login'], {
            queryParams: { reason: 'session-expired', returnUrl },
          });
          return throwError(() => error);
        }),
        switchMap((refreshedToken) => {
          if (contextChanged()) return throwError(changedContextError);
          return next(
            request.clone({
              withCredentials: true,
              context: request.context.set(AUTH_RETRY_ATTEMPTED, true),
              setHeaders: { Authorization: `Bearer ${refreshedToken}` },
            }),
          ).pipe(map(retainCurrentContext), catchError(retryError => throwError(() => contextChanged() ? changedContextError() : retryError)));
        }),
      );
    }),
  );
};

function isCookieSessionEndpoint(url: string): boolean {
  return /\/auth\/(login|refresh|logout)(?:[/?#]|$)/.test(url);
}
