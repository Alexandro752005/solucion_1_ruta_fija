import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';

export interface ServiceTypeSummary { readonly id:string; readonly name:string; readonly active:boolean; readonly version:number; readonly currentVersionId:string; }
export interface ServiceTypeVersion { readonly id:string; readonly revision:number; readonly name:string; readonly beforeMinutes:number; readonly afterMinutes:number; readonly contentSha256:string; }
export interface ServiceTypeView extends ServiceTypeSummary { readonly versions:readonly ServiceTypeVersion[]; readonly observedAt:string; }
export interface ServiceTypeCommand { readonly name:string; readonly beforeMinutes:number; readonly afterMinutes:number; readonly operationKey:string; readonly reason:string; }
export interface AssignmentCandidate { readonly driverId:string; readonly vehicleId:string; readonly periodId:string; }
export interface AssignmentCandidates { readonly serviceTypeVersionId:string; readonly reservationStartsAt:string; readonly reservationEndsAt:string; readonly items:readonly AssignmentCandidate[]; readonly nextOffset?:number|null; readonly observedAt:string; }

@Injectable({providedIn:'root'})
export class ServiceTypesApiService {
  private readonly http=inject(HttpClient); private readonly config=inject(RuntimeConfigService);
  private url(path=''):string { return this.config.apiUrl('/service-types'+path); }
  list(offset=0):Observable<readonly ServiceTypeSummary[]> { return this.http.get<readonly ServiceTypeSummary[]>(this.url(),{params:{limit:100,offset}}); }
  get(id:string):Observable<ServiceTypeView> { return this.http.get<ServiceTypeView>(this.url('/'+encodeURIComponent(id))); }
  operation(key:string):Observable<ServiceTypeView> { return this.http.get<ServiceTypeView>(this.url('/operations/'+encodeURIComponent(key))); }
  create(c:ServiceTypeCommand):Observable<ServiceTypeView> { return this.http.post<ServiceTypeView>(this.url(),c,this.options()); }
  revise(id:string,c:ServiceTypeCommand & {readonly expectedVersion:number}):Observable<ServiceTypeView> { return this.http.post<ServiceTypeView>(this.url('/'+encodeURIComponent(id)+'/versions'),c,this.options()); }
  status(id:string,c:{readonly expectedVersion:number; readonly active:boolean; readonly operationKey:string; readonly reason:string}):Observable<ServiceTypeView> { return this.http.post<ServiceTypeView>(this.url('/'+encodeURIComponent(id)+'/status'),c,this.options()); }
  candidates(c:{readonly serviceTypeVersionId:string; readonly routeVersionId:string; readonly scheduledAt:string; readonly scheduledEndAt:string; readonly offset?:number;readonly serviceId?:string}):Observable<AssignmentCandidates> {
    return this.http.get<AssignmentCandidates>(this.config.apiUrl('/assignment-candidates'),{params:{...c,limit:100,offset:c.offset??0}});
  }
  private options(){return {context:new HttpContext().set(SKIP_AUTH_REFRESH,true)};}
}
