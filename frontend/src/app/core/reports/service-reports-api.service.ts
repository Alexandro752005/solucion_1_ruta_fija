import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { IssueServiceReport, ServiceReportEmission, ServiceReportEmissionSummary, ServiceReportExportFormat, ServiceReportView } from './service-reports.models';

@Injectable({ providedIn: 'root' })
export class ServiceReportsApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);
  private url(path = ''): string { return this.config.apiUrl(`/reports/services${path}`); }
  current(from: string, to: string): Observable<ServiceReportView> {
    return this.http.get<ServiceReportView>(this.url(), { params: new HttpParams().set('from', from).set('to', to) });
  }
  issue(command: IssueServiceReport): Observable<ServiceReportEmission> {
    return this.http.post<ServiceReportEmission>(this.url('/emissions'), command);
  }
  list(from: string, to: string): Observable<readonly ServiceReportEmissionSummary[]> {
    return this.http.get<readonly ServiceReportEmissionSummary[]>(this.url('/emissions'), { params: new HttpParams().set('from', from).set('to', to) });
  }
  read(id: string): Observable<ServiceReportEmission> {
    return this.http.get<ServiceReportEmission>(this.url(`/emissions/${encodeURIComponent(id)}`));
  }
  export(id: string, format: ServiceReportExportFormat): Observable<Blob> {
    return this.http.get(this.url(`/emissions/${encodeURIComponent(id)}/export`), { params: { format }, responseType: 'blob' });
  }
}
