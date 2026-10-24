import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import { ConductorOnboardingExpediente, ConductorOnboardingSummary, DocumentaryExpediente, DocumentaryMutation, DocumentarySave, ExistingDriverRegularization, ExistingDriverRegularizationCommand } from './onboarding.models';

@Injectable({ providedIn: 'root' })
export class OnboardingApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  list(): Observable<readonly ConductorOnboardingSummary[]> {
    return this.http.get<readonly ConductorOnboardingSummary[]>(this.url());
  }

  regularizeExistingDriver(driverId: string, body: ExistingDriverRegularizationCommand): Observable<ExistingDriverRegularization> {
    return this.http.post<ExistingDriverRegularization>(
      this.runtimeConfig.apiUrl(`/drivers/${encodeURIComponent(driverId)}/regularization`), body,
      { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) },
    );
  }

  expediente(requestId: string): Observable<ConductorOnboardingExpediente> {
    return this.http.get<ConductorOnboardingExpediente>(
      `${this.url()}/${encodeURIComponent(requestId)}/expediente`,
    );
  }

  documentary(requestId: string): Observable<DocumentaryExpediente> {
    return this.http.get<DocumentaryExpediente>(`${this.url()}/${encodeURIComponent(requestId)}/documentary-expediente`);
  }

  saveDocumentary(requestId: string, body: DocumentarySave): Observable<DocumentaryExpediente> {
    return this.http.put<DocumentaryExpediente>(`${this.url()}/${encodeURIComponent(requestId)}/documentary-expediente`, body);
  }

  review(requestId: string, action: 'return' | 'extend' | 'approve' | 'reject' | 'provision' | 'provision-existing' | 'resubmit-existing',
    body: DocumentaryMutation & { readonly deadline?: string; readonly reiteration?: boolean; readonly groupId?: string },
  ): Observable<DocumentaryExpediente> {
    return this.http.post<DocumentaryExpediente>(`${this.url()}/${encodeURIComponent(requestId)}/${action}`, body,
      { context: new HttpContext().set(SKIP_AUTH_REFRESH, action === 'provision-existing' || action === 'resubmit-existing') });
  }

  private url(): string {
    return this.runtimeConfig.apiUrl('/onboarding/requests');
  }
}
