import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import { Assignment } from '../operations/operations.models';

export interface RouteStop { readonly key: string; readonly name: string; readonly kind: 'START' | 'INTERMEDIATE' | 'END'; readonly latitude?: number | null; readonly longitude?: number | null; }
export interface RouteVersion { readonly id: string; readonly revision: number; readonly name: string; readonly stops: readonly RouteStop[]; readonly contentSha256: string; readonly createdAt: string; }
export interface RouteSummary { readonly id: string; readonly name: string; readonly active: boolean; readonly version: number; readonly currentVersionId: string; }
export interface RouteView extends RouteSummary { readonly versions: readonly RouteVersion[]; readonly observedAt: string; }
export interface RouteCommand { readonly name: string; readonly operationKey: string; readonly reason: string; readonly stops: readonly RouteStop[]; }
export interface AssignedRoute { readonly assignmentId: string; readonly versionId?: string | null; readonly referenceStatus: string; readonly route?: RouteVersion | null; readonly declaration?: { readonly declaredAt: string; readonly terminalResult: string; readonly actorId: string } | null; readonly replacementAssignmentId?: string | null; }

@Injectable({ providedIn: 'root' })
export class RoutesApiService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(RuntimeConfigService);
  private url(path = ''): string { return this.config.apiUrl(`/routes${path}`); }
  list(offset = 0): Observable<readonly RouteSummary[]> { return this.http.get<readonly RouteSummary[]>(this.url(), { params: { limit: 100, offset } }); }
  get(id: string): Observable<RouteView> { return this.http.get<RouteView>(this.url(`/${encodeURIComponent(id)}`)); }
  operation(key: string): Observable<RouteView> { return this.http.get<RouteView>(this.url(`/operations/${encodeURIComponent(key)}`)); }
  assigned(id: string): Observable<AssignedRoute> { return this.http.get<AssignedRoute>(this.config.apiUrl(`/assignments/${encodeURIComponent(id)}/route`)); }
  replace(id: string, c: { readonly expectedVersion: number; readonly routeVersionId: string; readonly operationKey: string; readonly reason: string }): Observable<Assignment> { return this.http.post<Assignment>(this.config.apiUrl(`/assignments/${encodeURIComponent(id)}/route-replacement`), c, this.options()); }
  replacementOperation(key: string): Observable<Assignment> { return this.http.get<Assignment>(this.config.apiUrl(`/assignment-route-operations/${encodeURIComponent(key)}`)); }
  create(c: RouteCommand): Observable<RouteView> { return this.http.post<RouteView>(this.url(), c, this.options()); }
  revise(id: string, c: RouteCommand & { readonly expectedVersion: number }): Observable<RouteView> { return this.http.post<RouteView>(this.url(`/${encodeURIComponent(id)}/versions`), c, this.options()); }
  status(id: string, c: { readonly expectedVersion: number; readonly operationKey: string; readonly reason: string; readonly active: boolean }): Observable<RouteView> { return this.http.post<RouteView>(this.url(`/${encodeURIComponent(id)}/status`), c, this.options()); }
  private options() { return { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) }; }
}
