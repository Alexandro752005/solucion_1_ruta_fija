import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
import { ServiceTypesPage } from './service-types.page';

describe('L04 explicit service type configuration',()=>{
  let http:HttpTestingController;
  const old={id:'version-one',revision:1,name:'SYNTHETIC TYPE',beforeMinutes:5,afterMinutes:10,contentSha256:'a'.repeat(64)};
  const view={id:'type-one',name:'SYNTHETIC TYPE',version:1,active:true,currentVersionId:old.id,versions:[old],observedAt:'2030-01-01T00:00:00Z'};
  beforeEach(()=>{TestBed.configureTestingModule({imports:[ServiceTypesPage],providers:[provideRouter([]),provideHttpClient(),provideHttpClientTesting()]});http=TestBed.inject(HttpTestingController);});
  afterEach(()=>http.verify());
  function setup(){const f=TestBed.createComponent(ServiceTypesPage);f.detectChanges();http.expectOne(r=>r.url==='/api/v1/service-types').flush([]);return f.componentInstance;}
  it('never supplies an implicit zero margin or saves incomplete values',()=>{
    const c=setup();expect(c.form.controls.beforeMinutes.value).toBeNull();expect(c.form.controls.afterMinutes.value).toBeNull();
    c.save();http.expectNone(r=>r.method==='POST');
  });
  it('sends explicitly entered values and blocks auth replay for the configuration write',()=>{
    const c=setup();c.form.setValue({name:'SYNTHETIC TYPE',beforeMinutes:0,afterMinutes:10,reason:'SYNTHETIC-REVIEW'});c.save();
    const r=http.expectOne('/api/v1/service-types');expect(r.request.body.beforeMinutes).toBe(0);expect(r.request.body.afterMinutes).toBe(10);
    expect(r.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);r.flush(view);http.expectOne(r=>r.url==='/api/v1/service-types').flush([view]);
    expect(c.selected()?.id).toBe(view.id);
  });
  it('creates a new version with optimistic control and retains the displayed older version',()=>{
    const c=setup();c.open(view.id);http.expectOne('/api/v1/service-types/'+view.id).flush(view);c.editSelected();
    expect(c.form.controls.reason.value).toBe('');c.form.patchValue({afterMinutes:20,reason:'SYNTHETIC-REVISION'});c.save();
    const r=http.expectOne('/api/v1/service-types/'+view.id+'/versions');expect(r.request.body.expectedVersion).toBe(1);
    r.flush({...view,version:2,currentVersionId:'version-two',versions:[old,{...old,id:'version-two',revision:2,afterMinutes:20}]});
    http.expectOne(r=>r.url==='/api/v1/service-types').flush([]);expect(c.selected()?.versions[0].afterMinutes).toBe(10);
  });
  it('requires a reason to retire and preserves the existing revisions in the result',()=>{
    const c=setup();c.open(view.id);http.expectOne('/api/v1/service-types/'+view.id).flush(view);c.changeStatus();http.expectNone(r=>r.method==='POST');
    c.form.controls.reason.setValue('SYNTHETIC-RETIRE');c.changeStatus();const r=http.expectOne('/api/v1/service-types/'+view.id+'/status');
    expect(r.request.body.active).toBe(false);expect(r.request.body.expectedVersion).toBe(1);r.flush({...view,active:false,version:2});http.expectOne(r=>r.url==='/api/v1/service-types').flush([]);
    expect(c.selected()?.versions.length).toBe(1);
  });
  it('reconciles an uncertain write by its operation key without posting again',()=>{
    const c=setup();c.form.setValue({name:'SYNTHETIC TYPE',beforeMinutes:5,afterMinutes:10,reason:'SYNTHETIC-REVIEW'});c.save();
    const r=http.expectOne('/api/v1/service-types'),key=r.request.body.operationKey;r.error(new ProgressEvent('lost'));expect(c.uncertain()).toBe(true);
    c.save();http.expectNone(r=>r.method==='POST');c.reconcile();http.expectOne('/api/v1/service-types/operations/'+key).flush(view);
    http.expectOne(r=>r.url==='/api/v1/service-types').flush([]);expect(c.uncertain()).toBe(false);
  });
  it('ignores a late detail after explicitly starting a new configuration',()=>{
    const c=setup();c.open(view.id);const r=http.expectOne('/api/v1/service-types/'+view.id);c.newType();r.flush(view);
    expect(c.selected()).toBeNull();expect(c.form.controls.beforeMinutes.value).toBeNull();
  });
});
