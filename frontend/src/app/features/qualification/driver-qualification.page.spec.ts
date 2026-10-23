import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
import { Qualification } from '../../core/qualification/qualification.models';
import { DriverQualificationPage } from './driver-qualification.page';

const endpoint = '/api/v1/drivers/driver-synthetic/qualification';
const document = {
  requirementId: 'D-LICENSE', fields: { licenseNumber: 'SYNTHETIC-LICENSE', fullName: 'PERSONA SINTÉTICA' },
  expiryMode: 'DATED' as const, validUntil: '2027-10-18T04:59:59Z', applicability: 'CONFIRMED' as const,
  condition: '', sourceReference: 'SYNTHETIC-SOURCE', result: 'CONFORME' as const,
  reviewReference: 'SYNTHETIC-REVIEW', observation: '',
};
const state: Qualification = {
  driverId: 'driver-synthetic', accountId: 'account-synthetic', requestId: 'request-synthetic',
  version: 4, suspended: false, operationalEnabled: false,
  personalDocumentsStatus: 'VALID', personalValidUntil: '2027-10-18T04:59:59Z',
  blockers: ['UNIT_PERIOD_REQUIRED'], periods: [], unitRequests: [],
  catalog: [{ scope: 'VEHICLE', id: 'SYN-V', title: 'REQUISITO SINTÉTICO DE UNIDAD', mandatory: true,
    fieldKeys: ['category'] }, { scope: 'COMPANY', id: 'SYN-C', title: 'REQUISITO SINTÉTICO DE EMPRESA', mandatory: true,
    fieldKeys: ['titleNumber'] }],
  reviews: [{ scope: 'VEHICLE', requirementId: 'SYN-V', vehicleId: 'vehicle-synthetic',
    fields: { category: 'SYNTHETIC-PII' }, expiryMode: 'UNKNOWN', validUntil: null,
    applicability: 'POR_CONTRASTAR', condition: 'SYNTHETIC-CONDITION', sourceReference: 'SYNTHETIC-PRIVATE-SOURCE',
    result: 'POR_CONTRASTAR', reviewReference: 'SYNTHETIC-PRIVATE-REVIEW', version: 1 }],
  personalDocuments: [document], observedAt: '2026-10-18T15:00:00Z',
};

describe('DriverQualificationPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DriverQualificationPage>;
  let page: DriverQualificationPage;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverQualificationPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ driverId: 'driver-synthetic' }) } } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DriverQualificationPage);
    page = fixture.componentInstance;
    http.expectOne(endpoint).flush(state);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function openRestricted(): void {
    page.load(true);
    const query = http.expectOne(`${endpoint}/expediente`);
    expect(query.request.method).toBe('GET');
    expect(query.request.headers.get('Cache-Control')).toBe('no-store');
    query.flush(state);
    fixture.detectChanges();
  }

  it('consulta estado ordinario sin cargar ni conservar los datos personales devueltos indebidamente', () => {
    expect(page.restricted()).toBe(false);
    expect(page.state()?.personalDocuments).toEqual([]);
    expect(page.state()?.reviews[0]?.fields).toEqual({});
    expect(page.state()?.reviews[0]?.reviewReference).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('PERSONA SINTÉTICA');
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-PII');
    http.expectNone(`${endpoint}/expediente`);
  });

  it('abre el expediente solo por acción explícita y limpia los campos al cerrar', () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    openRestricted();
    expect(page.personalDrafts[0]?.fields['fullName']).toBe('PERSONA SINTÉTICA');
    expect(fixture.nativeElement.textContent).toContain('Expediente restringido');
    page.closeRestricted();
    expect(page.personalDrafts).toEqual([]);
    expect(page.reviewDrafts).toEqual([]);
    http.expectOne(endpoint).flush(state);
    fixture.detectChanges();
    expect(page.state()?.personalDocuments).toEqual([]);
    expect(fixture.nativeElement.textContent).not.toContain('PERSONA SINTÉTICA');
    expect(storage).not.toHaveBeenCalled();
  });

  it('limpia PII también al destruir la página', () => {
    openRestricted();
    page.ngOnDestroy();
    expect(page.personalDrafts).toEqual([]);
    expect(page.reviewDrafts).toEqual([]);
    expect(page.state()).toBeNull();
  });

  it('no modifica sin motivo ni envía éxito local antes de la respuesta HTTP', () => {
    page.act('activate');
    http.expectNone(`${endpoint}/activate`);
    expect(page.error()).toContain('motivo');
    page.reason = 'Comprobación sintética';
    page.act('activate');
    const request = http.expectOne(`${endpoint}/activate`);
    expect(page.saving()).toBe(true);
    expect(page.notice()).toBeNull();
    expect(request.request.body.expectedVersion).toBe(4);
    expect(request.request.body.operationKey).toMatch(/^[a-z0-9-]{36}$/);
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    request.flush({ ...state, version: 5, operationalEnabled: true });
    expect(page.state()?.operationalEnabled).toBe(true);
    expect(page.notice()).toContain('confirmado');
  });

  it('conserva clave ante pérdida de respuesta, bloquea hasta reconsulta y nunca reenvía automáticamente', () => {
    page.reason = 'Comprobación sintética';
    page.act('activate');
    const request = http.expectOne(`${endpoint}/activate`);
    const key = request.request.body.operationKey;
    request.error(new ProgressEvent('error'));
    expect(page.uncertain()).toBe(true);
    expect(page.lastOperationKey()).toBe(key);
    expect(page.state()).toBeNull();
    page.act('activate');
    http.expectNone(`${endpoint}/activate`);
    page.load();
    http.expectOne(endpoint).flush({ ...state, version: 5, operationalEnabled: true });
    expect(page.uncertain()).toBe(false);
    expect(page.lastOperationKey()).toBe(key);
    expect(page.notice()).toContain('no se ha reenviado');
    http.expectNone(`${endpoint}/activate`);
  });

  it('trata 409 como conflicto, sin asumir éxito ni reintentar', () => {
    page.reason = 'Comprobación sintética';
    page.act('activate');
    http.expectOne(`${endpoint}/activate`).flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.mustRefresh()).toBe(true);
    expect(page.error()).toContain('conflicto');
    expect(page.notice()).toBeNull();
    page.act('activate');
    http.expectNone(`${endpoint}/activate`);
  });

  it('representa denegación y aislamiento sin conservar el expediente', () => {
    page.load(true);
    http.expectOne(`${endpoint}/expediente`).flush({}, { status: 404, statusText: 'Not found' });
    expect(page.error()).toContain('organización');
    expect(page.state()).toBeNull();
    expect(page.personalDrafts).toEqual([]);
  });

  it('no convierte una vigencia desconocida ni aplicabilidad por contrastar en cumplimiento', () => {
    openRestricted();
    expect(page.reviewDrafts[0]?.expiryMode).toBe('UNKNOWN');
    expect(page.reviewDrafts[0]?.result).toBe('POR_CONTRASTAR');
    expect(page.reviewDrafts[0]?.applicability).toBe('POR_CONTRASTAR');
  });

  it('separa empresa y unidad al guardar revisión estructurada sin archivos', () => {
    openRestricted();
    page.scope = 'COMPANY';
    page.selectReviewScope();
    page.reason = 'Cotejo sintético de empresa';
    page.reviewDrafts[0]!.fields['titleNumber'] = 'SYNTHETIC-TITLE';
    page.saveReviews();
    const request = http.expectOne(`${endpoint}/reviews`);
    expect(request.request.body.scope).toBe('COMPANY');
    expect(request.request.body.vehicleId).toBeNull();
    expect(request.request.body.documents).toHaveLength(1);
    expect(request.request.body.documents[0].requirementId).toBe('SYN-C');
    expect(request.request.body.documents[0]).not.toHaveProperty('scope');
    expect(request.request.body.documents[0]).not.toHaveProperty('vehicleId');
    expect(request.request.body.documents[0]).not.toHaveProperty('version');
    expect(request.request.body).not.toHaveProperty('file');
    request.flush({ ...state, version: 5 });
  });

  it('exige unidad para revisión VEHICLE y conserva su referencia en el comando', () => {
    openRestricted();
    page.reason = 'Cotejo sintético de unidad';
    page.saveReviews();
    http.expectNone(`${endpoint}/reviews`);
    page.reviewVehicleId = 'vehicle-synthetic';
    page.selectReviewScope();
    page.saveReviews();
    const request = http.expectOne(`${endpoint}/reviews`);
    expect(request.request.body.vehicleId).toBe('vehicle-synthetic');
    request.flush({ ...state, version: 5 });
  });

  it('envía fechas de período para que el servidor incluya el día final en Lima', () => {
    page.reason = 'Período sintético';
    page.vehicleId = 'vehicle-synthetic';
    page.kind = 'COMPANY';
    page.start = '2026-10-18';
    page.createPeriod();
    http.expectNone(`${endpoint}/periods`);
    page.end = '2026-10-19';
    page.createPeriod();
    const request = http.expectOne(`${endpoint}/periods`);
    expect(request.request.body).toMatchObject({ startDate: '2026-10-18', endDate: '2026-10-19',
      startAt: null, endAt: null, kind: 'COMPANY' });
    request.flush({ ...state, version: 5 });
  });

  it('no infiere zona horaria del navegador para un instante', () => {
    page.reason = 'Período sintético';
    page.vehicleId = 'vehicle-synthetic';
    page.timeMode = 'INSTANT';
    page.start = '2026-10-18T10:00:00';
    page.createPeriod();
    http.expectNone(`${endpoint}/periods`);
    expect(page.error()).toContain('desplazamiento');
    page.start = '2026-10-18T10:00:00-05:00';
    page.createPeriod();
    const request = http.expectOne(`${endpoint}/periods`);
    expect(request.request.body).toMatchObject({ startAt: '2026-10-18T10:00:00-05:00', startDate: null, endAt: null });
    request.flush({ ...state, version: 5 });
  });

  it('exige prueba de revisión para suspender sin alterar disponibilidad localmente', () => {
    page.reason = 'Suspensión sintética';
    page.act('suspend');
    http.expectNone(`${endpoint}/suspend`);
    page.evidenceReference = 'SYNTHETIC-EVIDENCE';
    page.act('suspend');
    const request = http.expectOne(`${endpoint}/suspend`);
    expect(request.request.body.evidenceReference).toBe('SYNTHETIC-EVIDENCE');
    request.flush({ ...state, version: 5, suspended: true });
    expect(page.state()?.suspended).toBe(true);
  });

  it('renueva solo documentos existentes con cotejo expreso de la misma persona', () => {
    openRestricted();
    page.reason = 'Renovación sintética';
    page.savePersonal();
    http.expectNone(`${endpoint}/personal-documents`);
    page.originalReviewed = page.samePersonConfirmed = true;
    page.personalReviewReference = 'SYNTHETIC-MATERIAL-CHECK';
    page.savePersonal();
    const request = http.expectOne(`${endpoint}/personal-documents`);
    expect(request.request.body).toMatchObject({ samePersonConfirmed: true, originalReviewed: true,
      reviewReference: 'SYNTHETIC-MATERIAL-CHECK' });
    expect(request.request.body.documents).toHaveLength(1);
    expect(request.request.body.documents[0].validUntil).toBe('2027-10-18T05:00:00.000Z');
    request.flush({ ...state, version: 5 });
    expect(page.originalReviewed).toBe(false);
    expect(page.samePersonConfirmed).toBe(false);
  });

  it('decide una solicitud sin crear período ni activar conductor', () => {
    page.load();
    http.expectOne(endpoint).flush({ ...state, unitRequests: [{ id: 'unit-request-synthetic', vehicleId: null,
      kind: 'OWN', status: 'PENDING_REVIEW', reference: 'SYNTHETIC-REFERENCE', createdAt: state.observedAt }] });
    page.reason = 'Decisión sintética';
    page.decide('unit-request-synthetic', true);
    http.expectNone(`${endpoint}/unit-requests/unit-request-synthetic/decision`);
    page.decisionVehicles['unit-request-synthetic'] = 'vehicle-synthetic';
    page.decide('unit-request-synthetic', true);
    const request = http.expectOne(`${endpoint}/unit-requests/unit-request-synthetic/decision`);
    expect(request.request.body).toMatchObject({ approved: true, vehicleId: 'vehicle-synthetic' });
    request.flush({ ...state, version: 5 });
    http.expectNone(`${endpoint}/periods`);
    http.expectNone(`${endpoint}/activate`);
  });

  it('consulta referencias de unidad por páginas sin conservar ni mostrar placas', () => {
    page.loadVehicleReferences();
    const request = http.expectOne((candidate) => candidate.url === '/api/v1/vehicles');
    expect(request.request.params.get('active')).toBe('true');
    expect(request.request.params.get('size')).toBe('50');
    request.flush({ items: [{ id: 'vehicle-synthetic', status: 'DISPONIBLE', active: true,
      plate: 'SYNTHETIC-PRIVATE-PLATE', brand: 'SYNTHETIC-PRIVATE-BRAND' }], totalPages: 2 });
    expect(page.vehicleReferences()).toEqual([{ id: 'vehicle-synthetic', status: 'DISPONIBLE' }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-PRIVATE-PLATE');
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-PRIVATE-BRAND');
  });

  it('muestra atención de servicios conservados con referencia y enlace de asignaciones', () => {
    page.load();
    http.expectOne(endpoint).flush({ ...state, serviceAttention: [{ id: 'attention-synthetic',
      assignmentId: 'assignment-synthetic', kind: 'SAFE_HANDOVER_REQUIRED', status: 'OPEN',
      scheduledAt: state.observedAt, scheduledEndAt: '2026-10-18T18:00:00Z' }] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Requiere detención y relevo seguro');
    const link = fixture.nativeElement.querySelector('a[href="/assignments?assignmentId=assignment-synthetic"]');
    expect(link).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('SERVICIO-assignment-synthetic');
  });

  it('corrige placa únicamente en expediente restringido con cotejo y limpia el dato', () => {
    page.reason = 'Corrección material sintética';
    page.correctVehicle();
    http.expectNone(`${endpoint}/vehicle-correction`);
    openRestricted();
    page.correctionVehicleId = 'vehicle-synthetic';
    page.correctionPlate = 'SYN-001';
    page.correctionReviewReference = 'SYNTHETIC-UNIT-CHECK';
    page.correctVehicle();
    http.expectNone(`${endpoint}/vehicle-correction`);
    page.correctionOriginalReviewed = true;
    page.correctVehicle();
    const request = http.expectOne(`${endpoint}/vehicle-correction`);
    expect(request.request.body).toMatchObject({ vehicleId: 'vehicle-synthetic', plate: 'SYN-001',
      originalReviewed: true, reviewReference: 'SYNTHETIC-UNIT-CHECK', expectedVersion: 4 });
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    request.flush({ ...state, version: 5, blockers: ['VEH-01_UNIT_CHANGED_REVIEW_REQUIRED'] });
    expect(page.correctionPlate).toBe('');
    expect(page.correctionOriginalReviewed).toBe(false);
    expect(page.state()?.operationalEnabled).toBe(false);
    http.expectNone('/api/v1/vehicles/vehicle-synthetic');
  });

  it('al cerrar limpia placa y al cargar medianoche exclusiva mantiene el último día documental', () => {
    page.load(true);
    http.expectOne(`${endpoint}/expediente`).flush({ ...state, personalDocuments: [
      { ...document, validUntil: '2027-10-18T05:00:00.000Z' },
    ] });
    expect(page.personalDrafts[0]?.validUntil).toBe('2027-10-17');
    page.correctionPlate = 'SYN-001';
    page.closeRestricted();
    expect(page.correctionPlate).toBe('');
    http.expectOne(endpoint).flush(state);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#correction-plate')).toBeNull();
  });
});
