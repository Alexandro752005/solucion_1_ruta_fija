import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ServiceReportsComponent } from './service-reports.component';
import { ServiceReportsApiService } from '../../core/reports/service-reports-api.service';
import { ApiErrorService } from '../../core/api/api-error.service';
import { IssueServiceReport, ServiceReportEmission, ServiceReportExportFormat, ServiceReportView } from '../../core/reports/service-reports.models';

describe('Canonical reports and persisted emissions (API doubles)', () => {
  afterEach(() => TestBed.resetTestingModule());
  async function setup() {
    const view: ServiceReportView = { ruleVersion:'SYNTHETIC_V1', scope:'ORGANIZATION', from:'2026-10-01', to:'2026-10-21', zoneId:'America/Lima', cutoffAt:'2026-10-21T12:00:00Z', totals:{scheduled:0,completed:0,cancelled:0,interrupted:0,pending:0,transferred:0,completionPercent:null},coverage:{excludedLegacyOffers:2,canonicalServicesWithoutFirstOffer:0,notice:'SYNTHETIC_COVERAGE'},items:[] };
    const emission: ServiceReportEmission = {id:'synthetic-v1',seriesId:'synthetic-series',revision:1,previousEmissionId:null,issuedAt:view.cutoffAt,contentSha256:'f'.repeat(64),report:view};
    const api={ current:vi.fn(()=>of(view)), list:vi.fn(()=>of([emission])), issue:vi.fn((_command:IssueServiceReport)=>of(emission)), read:vi.fn((_id:string)=>of(emission)), export:vi.fn((_id:string,_format:ServiceReportExportFormat)=>of(new Blob(['SYNTHETIC']))) };
    await TestBed.configureTestingModule({imports:[ServiceReportsComponent],providers:[{provide:ServiceReportsApiService,useValue:api},{provide:ApiErrorService,useValue:{toUiError:()=>({message:'SYNTHETIC_REQUEST_FAILED',fieldErrors:[]})}}]}).compileComponents();
    const f=TestBed.createComponent(ServiceReportsComponent); const p=f.componentInstance; p.form.setValue({from:view.from,to:view.to}); f.detectChanges(); return {f,p,api,view,emission};
  }
  it('shows a null percentage as no denominator and discloses legacy coverage',async()=>{const{f}=await setup();expect(f.nativeElement.textContent).toContain('Sin denominador');expect(f.nativeElement.textContent).toContain('SYNTHETIC_COVERAGE');expect(f.nativeElement.textContent).toContain('Ofertas legadas excluidas 2');f.destroy();});
  it('retains a lost-response command and recovers with exactly the same key and dates',async()=>{
    const{f,p,api,emission}=await setup();api.issue.mockReturnValueOnce(throwError(()=>new Error('SYNTHETIC_LOST_RESPONSE'))); p.issue();
    const command=api.issue.mock.calls[0]![0];expect(p.pendingIssue()).toEqual(command);expect(p.selected()).toBeNull();p.issue();expect(api.issue).toHaveBeenCalledTimes(1);
    p.recoverIssue();expect(api.issue.mock.calls[1]![0]).toEqual(command);expect(p.pendingIssue()).toBeNull();expect(p.selected()).toEqual(emission);f.destroy();
  });
  it('does not silently issue after an edited period until that period was queried',async()=>{const{f,p,api}=await setup();p.form.controls.from.setValue('2026-10-02');p.issue();expect(api.issue).not.toHaveBeenCalled();expect(p.error()?.message).toContain('Consulta primero');f.destroy();});
  it('rejects an over90day range before making requests',async()=>{const{f,p,api}=await setup();api.current.mockClear();p.form.setValue({from:'2026-01-01',to:'2026-10-21'});p.refresh();expect(api.current).not.toHaveBeenCalled();expect(p.error()?.message).toContain('hasta90');f.destroy();});
  it('downloads CSV XLSX PDF from the selected emission without recomputing current view',async()=>{
    const{f,p,api}=await setup();p.select('synthetic-v1');api.current.mockClear();const create=vi.spyOn(URL,'createObjectURL').mockReturnValue('blob:synthetic');const revoke=vi.spyOn(URL,'revokeObjectURL').mockImplementation(()=>{});const click=vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});
    for(const format of ['csv','xlsx','pdf'] as const)p.download(format);expect(api.export.mock.calls).toEqual([['synthetic-v1','csv'],['synthetic-v1','xlsx'],['synthetic-v1','pdf']]);expect(api.current).not.toHaveBeenCalled();expect(p.selected()?.revision).toBe(1);create.mockRestore();revoke.mockRestore();click.mockRestore();f.destroy();
  });
  it('does not retain old data as a successful query after a server failure',async()=>{const{f,p,api}=await setup();api.current.mockReturnValueOnce(throwError(()=>new Error('SYNTHETIC_FAILURE')));p.refresh();expect(p.view()).toBeNull();expect(p.emissions()).toEqual([]);expect(p.error()?.message).toBe('SYNTHETIC_REQUEST_FAILED');f.destroy();});
  it('ignores a late version selection after a new range query',async()=>{const{f,p,api,emission}=await setup();const delayed=new Subject<ServiceReportEmission>();api.read.mockReturnValueOnce(delayed);p.select('synthetic-v1');p.refresh();delayed.next(emission);delayed.complete();expect(p.selected()).toBeNull();f.destroy();});
});
