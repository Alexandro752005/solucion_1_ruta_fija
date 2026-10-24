import { HttpBackend, HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import {
  AdminActivationAcceptance, AdminActivationClaim, AdminActivationOtp,
  AdminActivationStatus, AdminActivationTerms,
} from './admin-activation.models';

@Injectable({ providedIn: 'root' })
export class AdminActivationApiService {
  // This public flow must not inherit CRM credentials, session refresh or retries.
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly config = inject(RuntimeConfigService);

  claim(credential: string, newPassword: string): Observable<AdminActivationClaim> {
    return this.http.post<AdminActivationClaim>(this.url('/claim'), { credential, newPassword }, { credentials: 'omit' });
  }

  status(token: string): Observable<AdminActivationStatus> {
    return this.http.get<AdminActivationStatus>(this.url('/status'), this.options(token));
  }

  beginOtp(token: string): Observable<AdminActivationOtp> {
    return this.http.post<AdminActivationOtp>(this.url('/otp/begin'), null, this.options(token));
  }

  resendOtp(token: string, verificationToken: string): Observable<AdminActivationOtp> {
    return this.http.post<AdminActivationOtp>(this.url('/otp/resend'), { verificationToken }, this.options(token));
  }

  verifyOtp(token: string, verificationToken: string, code: string): Observable<AdminActivationStatus> {
    return this.http.post<AdminActivationStatus>(this.url('/otp/verify'), { verificationToken, code }, this.options(token));
  }

  terms(token: string): Observable<AdminActivationTerms> {
    return this.http.get<AdminActivationTerms>(this.url('/terms'), this.options(token));
  }

  acceptTerms(token: string, document: AdminActivationTerms): Observable<AdminActivationAcceptance> {
    return this.http.post<AdminActivationAcceptance>(this.url('/terms'), {
      version: document.version, contentSha256: document.contentSha256, accepted: true,
    }, this.options(token));
  }

  complete(token: string): Observable<AdminActivationStatus> {
    return this.http.post<AdminActivationStatus>(this.url('/complete'), null, this.options(token));
  }

  private options(token: string): { headers: HttpHeaders; withCredentials: false; credentials: 'omit' } {
    return { headers: new HttpHeaders({ 'X-Admin-Activation-Token': token }), withCredentials: false, credentials: 'omit' };
  }

  private url(path: string): string {
    return this.config.apiUrl(`/admin-activation${path}`);
  }
}
