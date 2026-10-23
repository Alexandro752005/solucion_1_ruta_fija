import {HttpClient,HttpContext} from '@angular/common/http';
import {inject,Injectable} from '@angular/core';
import {SKIP_AUTH_REFRESH} from '../auth/auth-http-context';
import {RuntimeConfigService} from '../config/runtime-config.service';
import {PageResult} from '../management/management.models';
export interface AnnouncementRecipient {readonly userId:string;readonly driverId:string;readonly label:string;readonly version:number;readonly accessStatus:string;readonly assignedAt:string;readonly deliveredAt?:string;readonly readAt?:string;readonly withdrawnAt?:string;readonly withdrawalKey?:string;readonly withdrawnBy?:string;}
export interface AnnouncementAttention {readonly announcementId:string;readonly requireReadAck:boolean;readonly recipients:number;readonly pending:number;readonly items:readonly AnnouncementRecipient[];}
export interface OperationNotice {readonly id:string;readonly userId:string;readonly eventType:string;readonly references:Readonly<Record<string,string>>;readonly createdAt:string;readonly readAt?:string;readonly externalStatus:string;readonly externalReason:string;}
@Injectable({providedIn:'root'})
export class CommunicationApiService {
  private readonly http=inject(HttpClient);private readonly config=inject(RuntimeConfigService);
  private url(p:string){return this.config.apiUrl(p);}
  attention(id:string){return this.http.get<AnnouncementAttention>(this.url('/announcements/'+encodeURIComponent(id)+'/attention'));}
  publication(key:string){return this.http.get<{announcementId:string;operationKey:string;createdAt:string}>(this.url('/announcements/operations/'+encodeURIComponent(key)));}
  withdraw(id:string,user:string,body:Readonly<Record<string,unknown>>){return this.http.post<AnnouncementAttention>(this.url('/announcements/'+encodeURIComponent(id)+'/recipients/'+encodeURIComponent(user)+'/withdraw'),body,{context:new HttpContext().set(SKIP_AUTH_REFRESH,true)});}
  inbox(failures:boolean,page=0){return this.http.get<PageResult<OperationNotice>>(this.url('/notices'),{params:{failures,page,size:20}});}
  readNotice(id:string){return this.http.post<OperationNotice>(this.url('/notices/'+encodeURIComponent(id)+'/read'),{},{context:new HttpContext().set(SKIP_AUTH_REFRESH,true)});}
}
