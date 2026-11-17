import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { VehiclesPage } from './vehicles.page';

const id='22222222-2222-4222-8222-222222222222';
describe('existing vehicle details from an ADMIN map intent',()=>{
  afterEach(()=>{TestBed.inject(HttpTestingController).verify();TestBed.resetTestingModule();});
  function setup(intent=id){
    TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting(),{provide:ActivatedRoute,useValue:{snapshot:{queryParamMap:convertToParamMap({vehicleId:intent,search:'TEST-01'})}}},
      {provide:AuthSessionService,useValue:{hasAnyRole:()=>true,user:()=>({role:'ADMIN',organizationName:'Empresa sintética'})}}]});
    const fixture=TestBed.createComponent(VehiclesPage),page=fixture.componentInstance,http=TestBed.inject(HttpTestingController);
    const request=http.expectOne(r=>r.url==='/api/v1/vehicles');return {fixture,page,http,request};
  }
  it('uses the existing tenant list and opens only the exact returned vehicle for consultation',()=>{
    const {page,http,request}=setup();expect(request.request.params.get('search')).toBe('TEST-01');expect(request.request.method).toBe('GET');
    request.flush({items:[{id,plate:'TEST-01',status:'EN_SERVICIO',active:true}],page:0,totalPages:1,totalItems:1,size:20});
    expect(page.detailOpen()).toBe(true);expect(page.selectedVehicle()?.id).toBe(id);expect(page.formOpen()).toBe(false);http.expectNone(r=>r.method!=='GET');
  });
  it('does not open another vehicle or bypass the existing list if an identifier is absent or malformed',()=>{
    const {page,request}=setup('invalid');expect(request.request.params.has('search')).toBe(false);
    request.flush({items:[{id,plate:'TEST-01',status:'EN_SERVICIO',active:true}],page:0,totalPages:1,totalItems:1,size:20});expect(page.detailOpen()).toBe(false);
  });
});
