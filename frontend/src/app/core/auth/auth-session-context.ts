import { CRM_ROLES, SessionUser } from './auth.models';

/** Stable CRM actor boundary. A token refresh or cosmetic name change is not a new actor. */
export function crmSessionContextKey(user: SessionUser | null | undefined): string | null {
  if (!user || typeof user.id !== 'string' || !user.id.trim() || !CRM_ROLES.includes(user.role)) return null;
  if (user.organizationId != null && typeof user.organizationId !== 'string') return null;
  return JSON.stringify([user.id.trim(), user.role, user.organizationId?.trim() || null]);
}
