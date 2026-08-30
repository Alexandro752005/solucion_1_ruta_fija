import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthSessionService } from './auth-session.service';
import { UserRole } from './auth.models';

export const roleGuard: CanActivateFn = (route) => {
  const session = inject(AuthSessionService);
  const router = inject(Router);
  const allowedRoles = route.data['roles'] as readonly UserRole[] | undefined;

  if (allowedRoles === undefined || allowedRoles.length === 0) {
    return true;
  }

  return session.hasAnyRole(allowedRoles)
    ? true
    : router.createUrlTree(['/forbidden']);
};
