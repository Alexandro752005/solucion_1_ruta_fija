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

  function loadCurrent(current: Qualification): void {
    page.load();
    http.expectOne(endpoint).flush(current);
    fixture.detectChanges();
  }

  function prepareCompanyPeriod(): void {
    page.reason = 'Período sintético';
    page.vehicleId = 'vehicle-synthetic';
    page.kind = 'COMPANY';
    page.start = '2026-11-10';
    page.end = '2026-12-24';
    page.preparePeriod();
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
    page.loadVehicleReferences();
    http.expectOne((request) => request.url === '/api/v1/vehicles').flush({ items: [
      { id: 'vehicle-synthetic', status: 'DISPONIBLE', active: true, plate: 'SYN-001' },
    ], totalPages: 1 });
    expect(page.restrictedVehicleLabels()['vehicle-synthetic']).toBe('SYN-001');
    page.ngOnDestroy();
    expect(page.personalDrafts).toEqual([]);
    expect(page.reviewDrafts).toEqual([]);
    expect(page.restrictedVehicleLabels()).toEqual({});
    expect(page.vehicleReferences()).toEqual([]);
    expect(page.state()).toBeNull();
  });

  it('no modifica sin motivo ni envía éxito local antes de la respuesta HTTP', () => {
    loadCurrent({ ...state, blockers: [] });
    page.act('activate');
    http.expectNone(`${endpoint}/activate`);
    expect(page.error()).toContain('motivo');
    page.reason = 'Comprobación sintética';
    page.act('activate');
    http.expectNone(`${endpoint}/activate`);
    expect(page.pendingConfirmation()?.action).toBe('activate');
    expect(page.state()?.operationalEnabled).toBe(false);
    expect(page.notice()).toBeNull();
    page.confirmPending();
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
    loadCurrent({ ...state, blockers: [] });
    page.reason = 'Comprobación sintética';
    page.act('activate');
    page.confirmPending();
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
    loadCurrent({ ...state, blockers: [] });
    page.reason = 'Comprobación sintética';
    page.act('activate');
    page.confirmPending();
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
    http.expectNone(`${endpoint}/periods`);
    expect(page.pendingConfirmation()?.period).toMatchObject({ start: '2026-10-18', end: '2026-10-19', kind: 'COMPANY' });
    page.confirmPending();
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
    http.expectNone(`${endpoint}/periods`);
    page.confirmPending();
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
    expect(page.restrictedVehicleLabels()).toEqual({});
    expect(page.unitLabel('vehicle-synthetic')).not.toContain('SYNTHETIC-PRIVATE-PLATE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-PRIVATE-PLATE');
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-PRIVATE-BRAND');
  });

  it('explica el vínculo autorizado vencido y conserva un pendiente desconocido como bloqueante', async () => {
    loadCurrent({ ...state, blockers: ['AUTHORIZED_UNIT_PERIOD_REQUIRED', 'FUTURE_PRIVATE-CONDITION'] });
    expect(fixture.nativeElement.querySelectorAll('.blocker-card')).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('Falta un vínculo de unidad vigente');
    expect(fixture.nativeElement.textContent).toContain('Hay una condición que requiere revisión');
    expect(fixture.nativeElement.textContent).not.toContain('FUTURE_PRIVATE-CONDITION');
    expect(page.stepStatus('period')).toBe('Pendiente');
    expect(page.stepStatus('activation')).toBe('Pendiente');
    expect(page.canActivate()).toBe(false);
    page.reason = 'Revisión sintética';
    page.prepareActivation();
    page.confirmPending();
    expect(page.pendingConfirmation()).toBeNull();
    http.expectNone(`${endpoint}/activate`);
    const button = fixture.nativeElement.querySelector('.blocker-card button') as HTMLButtonElement;
    button.click();
    await fixture.whenStable();
    expect(window.document.activeElement?.id).toBe('qualification-period-title');
  });

  it('continúa por el vínculo faltante sin detenerse en un paso que depende de elegir unidad', async () => {
    loadCurrent({ ...state, blockers: ['AUTHORIZED_UNIT_PERIOD_REQUIRED'] });
    expect(fixture.nativeElement.querySelectorAll('.qualification-steps button')).toHaveLength(4);
    (fixture.nativeElement.querySelector('.overview-actions button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(window.document.activeElement?.id).toBe('qualification-period-title');
    http.expectNone((request) => request.method === 'POST');
  });

  it('enfoca la revisión personal después de abrir y renderizar el expediente restringido', async () => {
    const button = fixture.nativeElement.querySelector('.qualification-steps button') as HTMLButtonElement;
    button.click();
    const request = http.expectOne(`${endpoint}/expediente`);
    expect(page.personalDrafts).toEqual([]);
    request.flush(state);
    await fixture.whenStable();
    expect(window.document.activeElement?.id).toBe('qualification-personal-title');
    expect(page.personalDrafts[0]?.fields['fullName']).toBe('PERSONA SINTÉTICA');
    http.expectNone((candidate) => candidate.method === 'POST');
  });

  it('permite elegir por placa solo tras abrir el expediente y pagina sin inventar un vínculo', async () => {
    page.openUnitPicker();
    http.expectNone('/api/v1/vehicles');
    const file = http.expectOne(`${endpoint}/expediente`);
    file.flush(state);
    const first = http.expectOne((request) => request.url === '/api/v1/vehicles');
    expect(first.request.params.get('page')).toBe('0');
    first.flush({ items: [{ id: 'vehicle-synthetic', status: 'DISPONIBLE', active: true,
      plate: 'SYN-001', brand: 'SYNTHETIC-PRIVATE-BRAND' }, { id: 'vehicle-inactive', status: 'INACTIVO',
      active: false, plate: 'SYN-999' }], totalPages: 2 });
    await fixture.whenStable();
    expect(window.document.activeElement?.id).toBe('qualification-period-title');
    const option = fixture.nativeElement.querySelector('.unit-option') as HTMLButtonElement;
    expect(option.textContent).toContain('SYN-001');
    expect(fixture.nativeElement.textContent).not.toContain('SYN-999');
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-PRIVATE-BRAND');
    option.click();
    await fixture.whenStable();
    expect(page.vehicleId).toBe('vehicle-synthetic');
    expect(page.reviewVehicleId).toBe('vehicle-synthetic');
    expect(option.getAttribute('aria-pressed')).toBe('true');
    page.loadVehicleReferences(1);
    const second = http.expectOne((request) => request.url === '/api/v1/vehicles');
    expect(second.request.params.get('page')).toBe('1');
    second.flush({ items: [{ id: 'vehicle-second', status: 'ASIGNADO', active: true, plate: 'SYN-002' }], totalPages: 2 });
    fixture.detectChanges();
    expect(page.vehiclePage()).toBe(1);
    expect(page.vehicleTotalPages()).toBe(2);
    expect(fixture.nativeElement.querySelector('.unit-option')?.textContent).toContain('SYN-002');
    expect(page.vehicleId).toBe('vehicle-synthetic');
    http.expectNone((request) => request.method === 'POST');
    page.closeRestricted();
    expect(page.restrictedVehicleLabels()).toEqual({});
    expect(page.vehicleReferences()).toEqual([]);
    http.expectOne(endpoint).flush(state);
    fixture.detectChanges();
    expect(page.unitLabel('vehicle-synthetic')).not.toContain('SYN-001');
    expect(fixture.nativeElement.textContent).not.toContain('SYN-001');
    expect(fixture.nativeElement.textContent).not.toContain('SYN-002');
  });

  it('cancela la consulta de unidades al cerrar e impide que una respuesta tardía repueble las placas', () => {
    openRestricted();
    page.loadVehicleReferences();
    const listing = http.expectOne((request) => request.url === '/api/v1/vehicles');
    page.closeRestricted();
    expect(listing.cancelled).toBe(true);
    expect(() => listing.flush({ items: [{ id: 'vehicle-synthetic', active: true,
      status: 'DISPONIBLE', plate: 'SYN-LATE' }], totalPages: 1 })).toThrow(/cancelled/);
    http.expectOne(endpoint).flush(state);
    fixture.detectChanges();
    expect(page.restrictedVehicleLabels()).toEqual({});
    expect(page.vehicleReferences()).toEqual([]);
    expect(fixture.nativeElement.textContent).not.toContain('SYN-LATE');
  });

  it('cancela el expediente pendiente al cerrar y no admite documentos de una respuesta tardía', () => {
    page.load(true);
    const file = http.expectOne(`${endpoint}/expediente`);
    page.closeRestricted();
    expect(file.cancelled).toBe(true);
    expect(() => file.flush(state)).toThrow(/cancelled/);
    http.expectOne(endpoint).flush(state);
    expect(page.restricted()).toBe(false);
    expect(page.personalDrafts).toEqual([]);
    expect(page.state()?.personalDocuments).toEqual([]);
  });

  it('borra las placas ya consultadas cuando falla la página siguiente del selector', () => {
    openRestricted();
    page.loadVehicleReferences();
    http.expectOne((request) => request.url === '/api/v1/vehicles').flush({ items: [
      { id: 'vehicle-synthetic', status: 'DISPONIBLE', active: true, plate: 'SYN-001' },
    ], totalPages: 2 });
    expect(page.restrictedVehicleLabels()['vehicle-synthetic']).toBe('SYN-001');
    page.loadVehicleReferences(1);
    http.expectOne((request) => request.url === '/api/v1/vehicles').flush({}, { status: 500, statusText: 'Server error' });
    expect(page.restrictedVehicleLabels()).toEqual({});
    expect(page.vehicleReferences()).toEqual([]);
    expect(page.vehicleError()).toContain('No se pudieron consultar');
    http.expectNone((request) => request.method === 'POST');
  });

  it('presenta resumen de empresa con fin obligatorio y espera confirmación separada', async () => {
    prepareCompanyPeriod();
    await fixture.whenStable();
    expect(window.document.activeElement?.id).toBe('qualification-confirmation-title');
    const summary = fixture.nativeElement.querySelector('.confirmation-panel') as HTMLElement;
    expect(summary.textContent).toContain('Unidad de empresa');
    expect(summary.textContent).toContain('2026-11-10');
    expect(summary.textContent).toContain('2026-12-24');
    expect(summary.textContent).toContain('último día incluido, Lima');
    expect(page.notice()).toBeNull();
    expect(page.state()?.operationalEnabled).toBe(false);
    http.expectNone((request) => request.method === 'POST');
    (fixture.nativeElement.querySelector('#qualification-confirm-pending') as HTMLButtonElement).click();
    const request = http.expectOne(`${endpoint}/periods`);
    expect(request.request.body).toMatchObject({ expectedVersion: 4, vehicleId: 'vehicle-synthetic', kind: 'COMPANY',
      startDate: '2026-11-10', endDate: '2026-12-24', startAt: null, endAt: null });
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(request.request.body.operationKey).toMatch(/^[a-z0-9-]{36}$/);
    request.flush({ ...state, version: 5, blockers: ['ACCOUNT_IDENTITY_GROUP_OR_DRIVER_REQUIRED'] });
    expect(page.state()?.operationalEnabled).toBe(false);
    expect(page.pendingConfirmation()).toBeNull();
    http.expectNone(`${endpoint}/activate`);
  });

  it('devuelve el foco al editor al cancelar y no guarda el vínculo', async () => {
    prepareCompanyPeriod();
    await fixture.whenStable();
    page.cancelConfirmation();
    await fixture.whenStable();
    expect(page.pendingConfirmation()).toBeNull();
    expect(page.start).toBe('2026-11-10');
    expect(page.end).toBe('2026-12-24');
    expect(window.document.activeElement?.id).toBe('qualification-prepare-period');
    page.confirmPending();
    http.expectNone((request) => request.method === 'POST');
  });

  it.each(['vehicle', 'kind', 'timeMode', 'start', 'end', 'reason', 'version'] as const)(
    'exige revisar de nuevo el resumen si cambia %s antes de confirmar', (changed) => {
      prepareCompanyPeriod();
      expect(page.pendingConfirmation()?.period?.end).toBe('2026-12-24');
      switch (changed) {
        case 'vehicle': page.vehicleId = 'vehicle-second'; break;
        case 'kind': page.kind = 'OWN'; break;
        case 'timeMode': page.timeMode = 'INSTANT'; break;
        case 'start': page.start = '2026-11-11'; break;
        case 'end': page.end = '2026-12-23'; break;
        case 'reason': page.reason = 'Otro motivo sintético'; break;
        case 'version': page.state.set({ ...state, version: 5 }); break;
      }
      page.confirmPending();
      expect(page.pendingConfirmation()).toBeNull();
      expect(page.error()).toContain('resumen cambió');
      expect(page.lastOperationKey()).toBeNull();
      http.expectNone((request) => request.method === 'POST');
    },
  );

  it.each([0, 409, 403])('no encadena habilitación ni repite el vínculo ante resultado %s', (status) => {
    prepareCompanyPeriod();
    page.confirmPending();
    const request = http.expectOne(`${endpoint}/periods`);
    const operationKey = request.request.body.operationKey;
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({}, { status, statusText: status === 409 ? 'Conflict' : 'Forbidden' });
    expect(page.mustRefresh()).toBe(true);
    expect(page.uncertain()).toBe(status === 0);
    expect(page.lastOperationKey()).toBe(operationKey);
    expect(page.notice()).toBeNull();
    expect(page.state()).toBeNull();
    expect(page.pendingConfirmation()).toBeNull();
    page.preparePeriod();
    page.prepareActivation();
    page.confirmPending();
    http.expectNone((candidate) => candidate.method === 'POST');
  });

  it('conserva la incertidumbre al cerrar durante un envío y exige reconsulta explícita', () => {
    openRestricted();
    prepareCompanyPeriod();
    page.confirmPending();
    const request = http.expectOne(`${endpoint}/periods`);
    const key = request.request.body.operationKey;
    page.closeRestricted();
    expect(request.cancelled).toBe(true);
    expect(page.restricted()).toBe(false);
    expect(page.personalDrafts).toEqual([]);
    expect(page.restrictedVehicleLabels()).toEqual({});
    expect(page.state()).toBeNull();
    expect(page.uncertain()).toBe(true);
    expect(page.mustRefresh()).toBe(true);
    expect(page.lastOperationKey()).toBe(key);
    http.expectNone((candidate) => candidate.method === 'GET');
    http.expectNone((candidate) => candidate.method === 'POST');
    page.load();
    http.expectOne(endpoint).flush({ ...state, version: 5, operationalEnabled: true, blockers: [] });
    expect(page.uncertain()).toBe(false);
    expect(page.lastOperationKey()).toBe(key);
    expect(page.notice()).toContain('no se ha reenviado');
    http.expectNone((candidate) => candidate.method === 'POST');
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
