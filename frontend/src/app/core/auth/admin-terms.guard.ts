import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AdminTermsApiService } from '../terms/admin-terms-api.service';
import { AuthSessionService } from './auth-session.service';

const ADMIN_TERMS_ROUTE = 'admin-terms';

export const adminTermsGuard: CanActivateChildFn = (route, state) => {
  const session = inject(AuthSessionService);
  if (session.user()?.role !== 'ADMIN' || route.routeConfig?.path === ADMIN_TERMS_ROUTE) {
    return true;
  }

  const termsApi = inject(AdminTermsApiService);
  const router = inject(Router);
  const termsRoute = router.createUrlTree(['/admin-terms'], {
    queryParams: { returnUrl: state.url },
  });

  return termsApi.getCurrentTerms().pipe(
    map((document) => (document.accepted ? true : termsRoute)),
    catchError(() => of(termsRoute)),
  );
};
