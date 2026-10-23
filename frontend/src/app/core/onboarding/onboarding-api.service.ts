import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { ConductorOnboardingExpediente, ConductorOnboardingSummary, DocumentaryExpediente, DocumentaryMutation, DocumentarySave } from './onboarding.models';

@Injectable({ providedIn: 'root' })
export class OnboardingApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  list(): Observable<readonly ConductorOnboardingSummary[]> {
    return this.http.get<readonly ConductorOnboardingSummary[]>(this.url());
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

  review(requestId: string, action: 'return' | 'extend' | 'approve' | 'reject' | 'provision',
    body: DocumentaryMutation & { readonly deadline?: string; readonly reiteration?: boolean; readonly groupId?: string },
  ): Observable<DocumentaryExpediente> {
    return this.http.post<DocumentaryExpediente>(`${this.url()}/${encodeURIComponent(requestId)}/${action}`, body);
  }

  private url(): string {
    return this.runtimeConfig.apiUrl('/onboarding/requests');
  }
}
