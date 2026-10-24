import {HttpClient,HttpContext} from '@angular/common/http';
import {inject,Injectable} from '@angular/core';
import {RuntimeConfigService} from '../config/runtime-config.service';
import {SKIP_AUTH_REFRESH} from '../auth/auth-http-context';
import {Incident} from './operations.models';
export interface IncidentAction {
  readonly id:string;readonly operationKey:string;readonly actorId:string;readonly kind:string;
  readonly detail:Readonly<Record<string,unknown>>;readonly recordedAt:string;readonly resultingVersion:number;
}
export interface IncidentOperation {readonly operationKey:string;readonly incidentId:string;readonly kind:string;readonly recordedAt:string;readonly resultingVersion:number;}
export interface IncidentResponsibleCandidate {readonly adminId:string;readonly label:string;}
@Injectable({providedIn:'root'})
export class IncidentWorkflowApiService {
  private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
  private url(path:string){return this.config.apiUrl('/incidents'+path);}
  get(id:string){return this.http.get<Incident>(this.url('/'+encodeURIComponent(id)));}
  history(id:string){return this.http.get<readonly IncidentAction[]>(this.url('/'+encodeURIComponent(id)+'/history'));}
  candidates(){return this.http.get<readonly IncidentResponsibleCandidate[]>(this.url('/responsible-candidates'));}
  operation(key:string){return this.http.get<IncidentOperation>(this.url('/operations/'+encodeURIComponent(key)));}
  act(id:string,kind:string,body:Readonly<Record<string,unknown>>){
    const context=new HttpContext().set(SKIP_AUTH_REFRESH,true),path=this.url('/'+encodeURIComponent(id)+'/'+kind);
    return kind==='follow-up'?this.http.patch<Incident>(path,body,{context}):this.http.post<Incident>(path,body,{context});
  }
}
