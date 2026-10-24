import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import {
  Qualification, QualificationCommand, QualificationPeriodCommand,
  QualificationPersonalCommand, QualificationReviewsCommand, QualificationVehicleCorrectionCommand,
} from './qualification.models';

@Injectable({ providedIn: 'root' })
export class QualificationApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);

  get(driverId: string, restricted = false): Observable<Qualification> {
    return this.http.get<Qualification>(this.url(driverId, restricted ? '/expediente' : ''), {
      headers: { 'Cache-Control': 'no-store' },
    });
  }

  reviews(driverId: string, command: QualificationReviewsCommand): Observable<Qualification> {
    return this.post(driverId, '/reviews', command);
  }

  period(driverId: string, command: QualificationPeriodCommand): Observable<Qualification> {
    return this.post(driverId, '/periods', command);
  }

  revoke(driverId: string, periodId: string, command: QualificationCommand): Observable<Qualification> {
    return this.post(driverId, `/periods/${encodeURIComponent(periodId)}/revoke`, command);
  }

  action(driverId: string, action: 'suspend' | 'resume' | 'activate',
    command: QualificationCommand & { readonly evidenceReference?: string }): Observable<Qualification> {
    return this.post(driverId, `/${action}`, command);
  }

  personal(driverId: string, command: QualificationPersonalCommand): Observable<Qualification> {
    return this.post(driverId, '/personal-documents', command);
  }

  vehicleCorrection(driverId: string, command: QualificationVehicleCorrectionCommand): Observable<Qualification> {
    return this.post(driverId, '/vehicle-correction', command);
  }

  decide(driverId: string, requestId: string,
    command: QualificationCommand & { readonly approved: boolean; readonly vehicleId: string | null }): Observable<Qualification> {
    return this.post(driverId, `/unit-requests/${encodeURIComponent(requestId)}/decision`, command);
  }

  private post(driverId: string, path: string, command: QualificationCommand): Observable<Qualification> {
    return this.http.post<Qualification>(this.url(driverId, path), command, {
      context: new HttpContext().set(SKIP_AUTH_REFRESH, true),
    });
  }

  private url(driverId: string, suffix = ''): string {
    return this.config.apiUrl(`/drivers/${encodeURIComponent(driverId)}/qualification${suffix}`);
  }
}
