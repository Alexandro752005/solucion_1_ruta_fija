import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SKIP_AUTH_REFRESH } from '../auth/auth-http-context';
import { LiveMapApiService } from './live-map-api.service';

describe('live map API boundary',()=>{
  let api:LiveMapApiService,http:HttpTestingController;
  beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});api=TestBed.inject(LiveMapApiService);http=TestBed.inject(HttpTestingController);});
  afterEach(()=>{http.verify();TestBed.resetTestingModule();});
  it('queries the minimal projection without a client tenant selector and avoids session retries retaining positions',()=>{
    let result:unknown;api.vehicles(10,'11111111-1111-4111-8111-111111111111',' Nombre sintético ').subscribe(value=>result=value);
    const request=http.expectOne(r=>r.url==='/api/v1/live-map/vehicles');
    expect(request.request.params.keys().sort()).toEqual(['driverName','groupId','limit']);
    expect(request.request.params.get('driverName')).toBe('Nombre sintético');expect(request.request.params.get('limit')).toBe('10');
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    request.flush({serverTime:'2026-11-11T12:00:00Z',limit:10,items:[]});expect(result).toEqual({serverTime:'2026-11-11T12:00:00Z',limit:10,items:[]});
  });
  it('requests groups as a minimal projection and rejects malformed telemetry rather than trusting its generic type',()=>{
    let groups:unknown,error:unknown;api.groups().subscribe(value=>groups=value);
    http.expectOne('/api/v1/live-map/groups').flush({items:[]});expect(groups).toEqual([]);
    api.vehicles(5,'','').subscribe({error:value=>error=value});http.expectOne(r=>r.url==='/api/v1/live-map/vehicles').flush({items:[{latitude:1000}]});
    expect(error).toBeInstanceOf(Error);
  });
});
