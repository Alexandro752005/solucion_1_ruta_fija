import { UserRole } from '../core/auth/auth.models';
import { InjectionToken } from '@angular/core';

// TB-A6 changes this source policy only after candidate QA. No user/query override.
export const TOPBAR_ENABLED_ROLES: readonly UserRole[] = ['ADMIN', 'SUPER_ADMIN'];
export const TOPBAR_ACTIVATION = new InjectionToken<readonly UserRole[]>('Topbar enabled roles', {
  providedIn: 'root', factory: () => TOPBAR_ENABLED_ROLES,
});
export function usesNewTopbar(role: UserRole | undefined, enabledRoles: readonly UserRole[] = TOPBAR_ENABLED_ROLES): boolean {
  return role !== undefined && enabledRoles.includes(role);
}
