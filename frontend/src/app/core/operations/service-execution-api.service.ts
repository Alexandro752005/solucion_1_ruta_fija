import { HttpClient,HttpContext } from '@angular/common/http';
import { inject,Injectable } from '@angular/core';
import { RuntimeConfigService } from '../config/runtime-config.service';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
export interface ExecutionSegment {readonly id:string;readonly assignmentId:string;readonly driverId:string;readonly vehicleId:string;readonly ordinal:number;readonly startedAt:string;readonly endedAt:string|null;readonly endKind:string|null;}
export interface GpsAccessRequest {readonly id:string;readonly driverId:string;readonly serviceId:string;readonly version:number;readonly status:string;readonly effectiveStatus:string;readonly fromAt:string;readonly toAt:string;readonly requestedAt:string;readonly reviewDueAt:string;readonly grantExpiresAt:string|null;}
export interface GpsReview {readonly expectedVersion:number;readonly approved:boolean;readonly identityConfirmed:boolean;readonly verificationReference:'RESTRICTED_IDENTITY_MATCH';readonly currentPassword:string;}
@Injectable({providedIn:'root'})
export class ServiceExecutionApiService {
  private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
  segments(serviceId:string){return this.http.get<readonly ExecutionSegment[]>(this.config.apiUrl('/scheduled-services/'+encodeURIComponent(serviceId)+'/execution'));}
  requests(serviceId:string,offset=0){return this.http.get<readonly GpsAccessRequest[]>(this.config.apiUrl('/gps/access-requests'),{params:{serviceId,limit:50,offset}});}
  review(id:string,body:GpsReview){return this.http.post<GpsAccessRequest>(this.config.apiUrl('/gps/access-requests/'+encodeURIComponent(id)+'/review'),body,
    {context:new HttpContext().set(SKIP_AUTH_REFRESH,true)});}
}
