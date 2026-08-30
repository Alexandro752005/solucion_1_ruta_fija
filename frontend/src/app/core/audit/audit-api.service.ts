import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import { AuditEvent, AuditEventPage, AuditEventQuery } from './audit.models';

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  list(query: AuditEventQuery = {}): Observable<AuditEventPage> {
    return this.http.get<AuditEventPage>(this.url('/audit-events'), {
      params: this.params(query),
    });
  }

  get(id: string): Observable<AuditEvent> {
    return this.http.get<AuditEvent>(this.url(`/audit-events/${id}`));
  }

  private url(path: string): string {
    return this.runtimeConfig.apiUrl(path);
  }

  private params(values: AuditEventQuery): HttpParams {
    return Object.entries(values).reduce(
      (params, [key, value]) =>
        value === undefined || value === '' ? params : params.set(key, String(value)),
      new HttpParams(),
    );
  }
}
