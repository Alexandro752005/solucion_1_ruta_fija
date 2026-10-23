import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';

export interface AccountIdentityStatus {
  readonly userId: string;
  readonly identityVerified: boolean;
}

export interface PlatformAdmin {
  readonly adminUserId: string;
  readonly organizationId: string;
  readonly active: boolean;
  readonly activationId: string | null;
  readonly status: string;
  readonly replacesAdminUserId: string | null;
  readonly credentialExpiresAt: string | null;
  readonly createdAt: string;
  readonly identityVerified: boolean;
}
export interface AdminActivationCredential {
  readonly adminUserId: string;
  readonly organizationId: string;
  readonly activationId: string;
  readonly status: string;
  readonly credential: string;
  readonly credentialExpiresAt: string;
}
export interface CreatePlatformAdmin {
  readonly organizationId: string;
  readonly email: string;
  readonly fullName: string;
  readonly reason: string;
  readonly identityReview: AdministrativeIdentityReview;
}
/** Input for the restricted administrative review only. Never returned by list/status endpoints. */
export interface AdministrativeIdentityReview {
  readonly documentType: 'DNI';
  readonly documentNumber: string;
  readonly method: 'PRIOR_SECURE_CONTACT' | 'IN_PERSON';
  readonly reference: string;
  readonly originalReviewed: true;
  readonly identityConfirmed: true;
}
export interface ReplacePlatformAdmin {
  readonly email: string;
  readonly fullName: string;
  readonly verificationMethod: 'PRIOR_SECURE_CONTACT' | 'IN_PERSON';
  readonly verificationReference: string;
  readonly reason: string;
  readonly identityChecked: true;
  readonly replacementAuthorized: true;
  readonly identityReview: AdministrativeIdentityReview;
}

@Injectable({ providedIn: 'root' })
export class PlatformAdminsApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);
  private url(id = ''): string { return this.config.apiUrl(`/platform/admins${id}`); }
  identityStatus(): Observable<AccountIdentityStatus> {
    return this.http.get<AccountIdentityStatus>(this.config.apiUrl('/identity/status'));
  }
  enrollOwner(currentPassword: string, identityReview: AdministrativeIdentityReview): Observable<AccountIdentityStatus> {
    return this.http.post<AccountIdentityStatus>(this.config.apiUrl('/platform/identity/self-enrollment'),
      { currentPassword, ownerDeclaration: true, identityReview }, { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) });
  }
  reviewIdentity(id: string, identityReview: AdministrativeIdentityReview, reason: string): Observable<AccountIdentityStatus> {
    return this.http.post<AccountIdentityStatus>(this.url(`/${encodeURIComponent(id)}/identity-review`),
      { identityReview, reason }, { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) });
  }
  list(): Observable<readonly PlatformAdmin[]> { return this.http.get<readonly PlatformAdmin[]>(this.url()); }
  create(body: CreatePlatformAdmin): Observable<AdminActivationCredential> { return this.http.post<AdminActivationCredential>(this.url(), body); }
  replace(id: string, body: ReplacePlatformAdmin): Observable<AdminActivationCredential> {
    return this.http.post<AdminActivationCredential>(this.url(`/${encodeURIComponent(id)}/replacement`), body);
  }
  rotateCredential(id: string, reason: string): Observable<AdminActivationCredential> {
    return this.http.post<AdminActivationCredential>(this.url(`/${encodeURIComponent(id)}/activation-credential`), { reason });
  }
  revoke(id: string, reason: string): Observable<PlatformAdmin> {
    return this.http.post<PlatformAdmin>(this.url(`/${encodeURIComponent(id)}/revoke`), { reason });
  }
}
