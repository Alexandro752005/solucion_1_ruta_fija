import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import {
  CreateInvitationRequest,
  CreatedInvitation,
  InvitationPage,
  InvitationSummary,
} from './invitations.models';

@Injectable({ providedIn: 'root' })
export class InvitationsApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  issue(request: CreateInvitationRequest): Observable<CreatedInvitation> {
    return this.http.post<CreatedInvitation>(this.url(), request);
  }

  list(page: number, size: number): Observable<InvitationPage> {
    return this.http.get<InvitationPage>(this.url(), {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  revoke(id: string): Observable<InvitationSummary> {
    return this.http.post<InvitationSummary>(
      `${this.url()}/${encodeURIComponent(id)}/revoke`,
      {},
    );
  }

  private url(): string {
    return this.runtimeConfig.apiUrl('/invitations');
  }
}
