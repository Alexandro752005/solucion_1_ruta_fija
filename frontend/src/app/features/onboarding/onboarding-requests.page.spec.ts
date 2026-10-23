import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { OnboardingRequestsPage } from './onboarding-requests.page';

describe('OnboardingRequestsPage', () => {
  const baseUrl = '/api/v1/onboarding/requests';
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OnboardingRequestsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('muestra solo el resumen por código y no solicita expedientes al cargar la lista', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cargando solicitudes');
    const request = http.expectOne(baseUrl);
    expect(request.request.method).toBe('GET');
    request.flush([{ ...summary(), ...personalData() }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('request-1');
    expect(fixture.nativeElement.textContent).toContain('Pendiente de revisión');
    expect(fixture.nativeElement.textContent).not.toContain('Persona Sintética');
    expect(fixture.nativeElement.textContent).not.toContain('person@example.test');
    expect(fixture.nativeElement.textContent).not.toContain('900000001');
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    http.expectNone(`${baseUrl}/request-1/expediente`);
  });

  it('carga PII solo al abrir expresamente el expediente y la elimina al cerrarlo', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    fixture.detectChanges();
    http.expectOne(baseUrl).flush([summary()]);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.open-expediente') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Cargando expediente');
    const request = http.expectOne(`${baseUrl}/request-1/expediente`);
    expect(request.request.method).toBe('GET');
    request.flush({ requestId: 'request-1', status: 'PENDING_REVIEW', ...personalData() });
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
    expect(dialog.textContent).toContain('Persona Sintética');
    expect(dialog.textContent).toContain('person@example.test');
    expect(dialog.textContent).toContain('900000001');

    (dialog.querySelector('.rf-modal__close') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.expediente()).toBeNull();
    expect(fixture.componentInstance.selectedRequestId()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Persona Sintética');
    expect(fixture.nativeElement.textContent).not.toContain('person@example.test');
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();

    // Reopening always performs a new authorized read; there is no stored detail.
    (fixture.nativeElement.querySelector('.open-expediente') as HTMLButtonElement).click();
    const freshRequest = http.expectOne(`${baseUrl}/request-1/expediente`);
    fixture.componentInstance.closeExpediente();
    expect(freshRequest.cancelled).toBe(true);
  });

  it('cancela la carga al cerrar y evita que una respuesta tardía repueble la vista', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.detectChanges();
    fixture.componentInstance.openExpediente('request-1');
    const request = http.expectOne(`${baseUrl}/request-1/expediente`);
    fixture.componentInstance.closeExpediente();
    fixture.detectChanges();

    expect(request.cancelled).toBe(true);
    expect(fixture.componentInstance.expediente()).toBeNull();
    expect(fixture.componentInstance.expedienteLoading()).toBe(false);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('limpia los datos personales cuando la navegación destruye la pantalla', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush({
      requestId: 'request-1', status: 'PENDING_REVIEW', ...personalData(),
    });
    expect(fixture.componentInstance.expediente()).not.toBeNull();
    fixture.destroy();

    expect(fixture.componentInstance.expediente()).toBeNull();
    expect(fixture.componentInstance.selectedRequestId()).toBeNull();
    expect(fixture.componentInstance.requests()).toEqual([]);
  });

  it('cancela la carga de expediente pendiente al salir de la pantalla', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    const request = http.expectOne(`${baseUrl}/request-1/expediente`);
    fixture.destroy();

    expect(request.cancelled).toBe(true);
    expect(fixture.componentInstance.expediente()).toBeNull();
  });

  it.each([
    [404, 'El expediente no está disponible para tu organización.'],
    [403, 'No tienes permiso para abrir este expediente.'],
    [503, 'No se pudo abrir el expediente. Cierra esta vista e intenta nuevamente.'],
  ])('informa fallo %s sin mostrar el cuerpo del servidor ni datos del expediente', (status, message) => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush(
      { message: 'person@example.test', ...personalData() },
      { status, statusText: 'Request failed' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(message);
    expect(fixture.nativeElement.textContent).not.toContain('person@example.test');
    expect(fixture.componentInstance.expediente()).toBeNull();
    expect(fixture.componentInstance.expedienteLoading()).toBe(false);
  });

  it('muestra denegación del listado y permite actualizar hasta el estado vacío', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No tienes permiso para consultar estas solicitudes.');

    (fixture.nativeElement.querySelector('section.management-panel header button') as HTMLButtonElement).click();
    http.expectOne(baseUrl).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay solicitudes');
    expect(fixture.nativeElement.querySelector('.open-expediente')).toBeNull();
  });

  it('consulta documentación solo dentro del expediente y guarda con versión e idempotencia', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush({ requestId: 'request-1', ...personalData() });
    http.expectNone(`${baseUrl}/request-1/documentary-expediente`);
    fixture.componentInstance.loadDocumentary();
    http.expectOne(`${baseUrl}/request-1/documentary-expediente`).flush(documentary());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[type="file"]')).toBeNull();
    fixture.componentInstance.draftDocuments[0].fields['documentNumber'] = 'synthetic-only';
    fixture.componentInstance.saveDocumentary();
    const save = http.expectOne(`${baseUrl}/request-1/documentary-expediente`);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.expectedVersion).toBe(3);
    expect(save.request.body.operationKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(save.request.body.documents[0].fields.documentNumber).toBe('synthetic-only');
    save.flush(documentary(4));
    expect(fixture.componentInstance.documentary()?.version).toBe(4);
    fixture.componentInstance.closeExpediente();
    expect(fixture.componentInstance.documentary()).toBeNull();
    expect(fixture.componentInstance.draftDocuments).toEqual([]);
  });

  it('exige motivo para devolver y conserva rondas confirmadas por servidor', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush({ requestId: 'request-1', ...personalData() });
    fixture.componentInstance.loadDocumentary();
    http.expectOne(`${baseUrl}/request-1/documentary-expediente`).flush(documentary());
    fixture.componentInstance.act('return');
    http.expectNone(`${baseUrl}/request-1/return`);
    fixture.componentInstance.reason = 'Subsanar vigencia del requisito.';
    fixture.componentInstance.act('return');
    const request = http.expectOne(`${baseUrl}/request-1/return`);
    expect(request.request.body.reason).toBe('Subsanar vigencia del requisito.');
    request.flush({ ...documentary(4), status: 'RETURNED', reviewRounds: 1 });
    expect(fixture.componentInstance.requests()[0].reviewRounds).toBe(1);
    expect(fixture.componentInstance.requests()[0].status).toBe('RETURNED');
  });

  it.each([0, 409])('consulta estado tras fallo %s sin reintentar una mutación', (status) => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush({ requestId: 'request-1', ...personalData() });
    fixture.componentInstance.loadDocumentary();
    http.expectOne(`${baseUrl}/request-1/documentary-expediente`).flush(documentary());
    fixture.componentInstance.act('approve');
    http.expectOne(`${baseUrl}/request-1/approve`).flush({}, { status, statusText: 'Unconfirmed' });
    expect(fixture.componentInstance.mustRefresh()).toBe(true);
    fixture.componentInstance.act('approve');
    http.expectNone(`${baseUrl}/request-1/approve`);
    fixture.componentInstance.loadDocumentary();
    http.expectOne(`${baseUrl}/request-1/documentary-expediente`).flush({ ...documentary(4), status: 'APPROVED', personalApproved: true });
    expect(fixture.componentInstance.mustRefresh()).toBe(false);
    expect(fixture.componentInstance.documentary()?.personalApproved).toBe(true);
  });

  it('separa aprobación personal del alta y exige grupo activo antes de provisionar', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush({ requestId: 'request-1', ...personalData() });
    fixture.componentInstance.loadDocumentary();
    http.expectOne(`${baseUrl}/request-1/documentary-expediente`).flush({ ...documentary(), status: 'APPROVED', personalApproved: true });
    fixture.componentInstance.act('provision');
    http.expectNone(`${baseUrl}/request-1/provision`);
    fixture.componentInstance.loadGroups();
    const groups = http.expectOne((request) => request.url === '/api/v1/groups');
    expect(groups.request.params.get('active')).toBe('true');
    groups.flush({ items: [{ id: 'group-active', active: true }, { id: 'group-inactive', active: false }], totalItems: 2, totalPages: 1, page: 0, size: 100 });
    expect(fixture.componentInstance.activeGroups().map((group) => group.id)).toEqual(['group-active']);
    fixture.componentInstance.groupId = 'group-active';
    fixture.componentInstance.act('provision');
    const provision = http.expectOne(`${baseUrl}/request-1/provision`);
    expect(provision.request.body.groupId).toBe('group-active');
    expect(provision.request.body.reason).toBeTruthy();
    provision.flush({ ...documentary(4), status: 'APPROVED', personalApproved: true, provisionedUserId: 'user-1', provisionedDriverId: 'driver-1' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No acredita unidad vinculada ni habilitación para conducir.');
    expect(fixture.nativeElement.textContent).toContain('Cuenta vinculada: user-1');
    expect(fixture.componentInstance.documentary()?.operationalEnabled).toBe(false);
  });

  it('convierte vigencia documental al final del día Perú y no envía fecha para sin vencimiento', () => {
    const fixture = TestBed.createComponent(OnboardingRequestsPage);
    http.expectOne(baseUrl).flush([summary()]);
    fixture.componentInstance.openExpediente('request-1');
    http.expectOne(`${baseUrl}/request-1/expediente`).flush({ requestId: 'request-1', ...personalData() });
    fixture.componentInstance.loadDocumentary();
    http.expectOne(`${baseUrl}/request-1/documentary-expediente`).flush(documentary());
    fixture.componentInstance.draftDocuments[0].expiryMode = 'DATED';
    fixture.componentInstance.draftDocuments[0].validUntil = '2027-05-10';
    fixture.componentInstance.saveDocumentary();
    const dated = http.expectOne(`${baseUrl}/request-1/documentary-expediente`);
    expect(dated.request.body.documents[0].validUntil).toBe('2027-05-10T23:59:59-05:00');
    dated.flush(documentary(4));
    fixture.componentInstance.draftDocuments[0].expiryMode = 'NO_EXPIRY';
    fixture.componentInstance.draftDocuments[0].validUntil = '2027-05-10';
    fixture.componentInstance.saveDocumentary();
    const undated = http.expectOne(`${baseUrl}/request-1/documentary-expediente`);
    expect(undated.request.body.documents[0].validUntil).toBeNull();
    undated.flush(documentary(5));
  });

  function summary() {
    return { requestId: 'request-1', status: 'PENDING_REVIEW', reviewRounds: 0, submittedAt: '2026-10-15T12:00:00Z' };
  }

  function personalData() {
    return { fullName: 'Persona Sintética', email: 'person@example.test', phone: '900000001' };
  }

  function documentary(version = 3) {
    return {
      requestId: 'request-1', status: 'PENDING_REVIEW', version, reviewRounds: 0, modality: 'TAXI',
      catalogId: 'L01-CHICLAYO', catalogVersion: '0.1.0', catalogHash: 'synthetic-hash',
      catalog: [{ requirementId: 'CON-01', title: 'Identidad', mandatory: true, fieldKeys: ['documentNumber'] }],
      documents: [], history: [], correctionDeadline: null, overdue: false, personalApproved: false,
      operationalEnabled: false, reapplyAfter: null,
    };
  }
});
