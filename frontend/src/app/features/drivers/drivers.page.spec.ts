import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AuthSessionService } from '../../core/auth/auth-session.service';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
import { Driver } from '../../core/management/management.models';
import { DriversPage } from './drivers.page';

describe('DriversPage privacy and onboarding policy', () => {
  let http: HttpTestingController;
  const allowedAdmin = signal(true);
  const driver: Driver = { id: 'driver-1', userId: 'account-1', groupId: 'group-1', groupName: 'Grupo académico',
    fullName: 'Persona Sintética', phone: '900000001', documentType: 'DNI', documentNumber: '99999999', licenseNumber: 'SYNTHETIC-LICENSE',
    availabilityStatus: 'DESCANSO', locationConsent: false, active: true, createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' };

  beforeEach(async () => {
    allowedAdmin.set(true);
    await TestBed.configureTestingModule({ imports: [DriversPage], providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthSessionService, useValue: { user: signal(null), hasAnyRole: () => allowedAdmin() } },
    ] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); });

  function create() {
    const fixture = TestBed.createComponent(DriversPage);
    http.expectOne((request) => request.url === '/api/v1/drivers').flush(page([driver]));
    http.expectOne((request) => request.url === '/api/v1/groups').flush(page([]));
    if (fixture.componentInstance.canManage()) http.expectOne((request) => request.url === '/api/v1/vehicles').flush(page([]));
    else http.expectNone((request) => request.url === '/api/v1/vehicles');
    http.expectNone((request) => request.url === '/api/v1/users');
    fixture.detectChanges();
    return fixture;
  }

  it('conceals PII in list and ordinary detail and links to restricted onboarding', () => {
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('CONDUCTOR-driver-1');
    fixture.componentInstance.viewDetails(driver);
    http.expectOne('/api/v1/drivers/driver-1').flush({ driver, vehicles: [] });
    http.expectOne('/api/v1/drivers/driver-1/incorporation-status').flush({
      driverId: driver.id, accountId: driver.userId, requestId: null, incorporationStatus: 'NO_LINKED_REQUEST',
      personalApproved: false, accountProvisioned: false, accountEnabled: true, identityReviewed: false,
      groupActive: true, driverEnabled: true, personalDocumentsStatus: 'UNKNOWN', activeVehicleLinks: 0,
      unitCardinalitySatisfied: true, operationalEnabled: false,
      blockers: ['PERSONAL_APPROVAL_MISSING', 'UNIT_AND_SERVICE_REVIEW_PENDING'], observedAt: '2026-10-18T12:00:00Z',
    });
    fixture.detectChanges();
    for (const privateValue of [driver.fullName, driver.phone!, driver.documentNumber, driver.licenseNumber!]) {
      expect(fixture.nativeElement.textContent).not.toContain(privateValue);
    }
    expect(fixture.nativeElement.querySelector('a[href="/onboarding-requests"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#driver-full-name')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="search"]')).toBeNull();
  });

  it('prevents legacy create/edit submissions from replacing identity with a reference code', () => {
    const fixture = create();
    fixture.componentInstance.startCreate();
    fixture.componentInstance.startEdit(driver);
    fixture.componentInstance.form.patchValue({ fullName: 'CONDUCTOR-driver-1', documentNumber: driver.documentNumber!, groupId: driver.groupId });
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.formOpen()).toBe(false);
    expect(fixture.componentInstance.form.controls.fullName.value).toBe('');
    http.expectNone((request) => request.method !== 'GET');
  });

  it.each(['DISPONIBLE', 'DESCANSO', 'NO_DISPONIBLE'])('cannot declare CONDUCTOR availability as ADMIN: %s', (status) => {
    const fixture = create();
    expect(fixture.componentInstance.canChangeAvailability(driver)).toBe(false);
    fixture.componentInstance.changeAvailability(driver, status);
    expect(fixture.componentInstance.pendingActionMessage()).toBe('');
    fixture.componentInstance.confirmPendingAction();
    http.expectNone((request) => request.method !== 'GET');
  });

  it('keeps only an identifier in administrative deactivation confirmation', () => {
    const fixture = create();
    fixture.componentInstance.toggleActive(driver);
    expect(fixture.componentInstance.pendingActionMessage()).toContain('driver-1');
    expect(fixture.componentInstance.pendingActionMessage()).not.toContain(driver.fullName);
    expect(fixture.componentInstance.pendingActionMessage()).not.toContain(driver.documentNumber);
    http.expectNone((request) => request.method !== 'GET');
  });

  function fillReview(component: DriversPage) {
    component.identityReviewForm.patchValue({ documentNumber: '00000002', method: 'IN_PERSON', reference: 'Cotejo sintético presencial',
      originalReviewed: true, identityConfirmed: true, reason: 'Regularización sintética autorizada.' });
  }

  it('opens only a restricted manual identity input without fetching or prefilling the legacy document', () => {
    const fixture = create();
    expect(fixture.nativeElement.querySelector('#driver-identity-document')).toBeNull();
    fixture.componentInstance.startIdentityReview(driver);
    fixture.detectChanges();
    expect(fixture.componentInstance.identityReviewTarget()).toEqual({ driverId: 'driver-1', userId: 'account-1' });
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.identityReviewForm.controls.originalReviewed.value).toBe(false);
    expect(fixture.componentInstance.identityReviewForm.controls.identityConfirmed.value).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Expediente restringido');
    expect(fixture.nativeElement.textContent).toContain('puede desactivar al conductor y revocar sus sesiones');
    expect(fixture.nativeElement.querySelector('input[type="file"]')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain(driver.documentNumber);
    http.expectNone((request) => request.method === 'GET');
  });

  it('requires a valid DNI and both explicit confirmations before posting', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityReview(driver);
    fillReview(fixture.componentInstance);
    fixture.componentInstance.identityReviewForm.controls.originalReviewed.setValue(false);
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.identityReviewForm.patchValue({ originalReviewed: true, identityConfirmed: false });
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.identityReviewForm.patchValue({ identityConfirmed: true, documentNumber: '0000002' });
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.identityReviewForm.controls.documentNumber.setValue('0000X002');
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((request) => request.method === 'POST');
  });

  it('sends only the agreed review, clears inputs immediately and reloads without claiming approval or activation', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityReview(driver);
    fillReview(fixture.componentInstance);
    fixture.componentInstance.submitIdentityReview();
    const request = http.expectOne('/api/v1/drivers/driver-1/identity-review');
    expect(request.request.method).toBe('POST');
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(request.request.body).toEqual({ identityReview: { documentType: 'DNI', documentNumber: '00000002', method: 'IN_PERSON',
      reference: 'Cotejo sintético presencial', originalReviewed: true, identityConfirmed: true }, reason: 'Regularización sintética autorizada.' });
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.identityReviewForm.controls.reference.value).toBe('');
    expect(fixture.componentInstance.identityReviewTarget()).toBeNull();
    request.flush({ userId: 'account-1', identityVerified: true });
    http.expectOne((item) => item.url === '/api/v1/drivers').flush(page([{ ...driver, active: false }]));
    fixture.detectChanges();
    expect(fixture.componentInstance.drivers()[0].active).toBe(false);
    expect(fixture.componentInstance.notice()).toContain('no aprueba el expediente documental ni habilita la operación');
    expect(fixture.nativeElement.textContent).not.toContain('00000002');
    expect(fixture.nativeElement.textContent).not.toContain('Persona Sintética');
    http.expectNone((item) => item.method !== 'GET');
  });

  it.each([0, 403, 409])('does not echo an error body or retry the review after an unconfirmed response %s', (status) => {
    const fixture = create();
    fixture.componentInstance.startIdentityReview(driver);
    fillReview(fixture.componentInstance);
    fixture.componentInstance.submitIdentityReview();
    http.expectOne('/api/v1/drivers/driver-1/identity-review').flush({ message: 'PRIVATE DNI 00000002', errors: [{ field: 'documentNumber', message: '00000002' }] }, { status, statusText: 'Unconfirmed' });
    fixture.detectChanges();
    expect(fixture.componentInstance.identityReviewMustRefresh()).toBe(true);
    expect(fixture.componentInstance.notice()).toBeNull();
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('00000002');
    expect(fixture.nativeElement.textContent).not.toContain('PRIVATE DNI');
    fixture.componentInstance.startIdentityReview(driver);
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((item) => item.method === 'POST');
    fixture.componentInstance.load();
    http.expectOne((item) => item.url === '/api/v1/drivers').flush(page([driver]));
    expect(fixture.componentInstance.identityReviewMustRefresh()).toBe(false);
  });

  it('requires an authorized ADMIN and an associated account without enabling legacy creation', () => {
    allowedAdmin.set(false);
    const fixture = create();
    fixture.componentInstance.startIdentityReview(driver);
    expect(fixture.componentInstance.identityReviewTarget()).toBeNull();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Revisar identidad restringida');
    allowedAdmin.set(true);
    fixture.componentInstance.startIdentityReview({ ...driver, userId: null });
    expect(fixture.componentInstance.identityReviewTarget()).toBeNull();
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((item) => item.method === 'POST');
  });

  it('clears the restricted form on cancel and navigation and cancels a pending subscription', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityReview(driver);
    fillReview(fixture.componentInstance);
    fixture.componentInstance.closeIdentityReview();
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.identityReviewForm.controls.originalReviewed.value).toBe(false);
    fixture.componentInstance.startIdentityReview(driver);
    fillReview(fixture.componentInstance);
    fixture.componentInstance.submitIdentityReview();
    const request = http.expectOne('/api/v1/drivers/driver-1/identity-review');
    fixture.destroy();
    expect(request.cancelled).toBe(true);
    expect(fixture.componentInstance.identityReviewTarget()).toBeNull();
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.identityReviewForm.controls.reason.value).toBe('');
  });

  function fillRegularization(component: DriversPage) {
    component.regularizationForm.patchValue({ reason: 'Regularización sintética con conservación del historial.', identityPreservedConfirmed: true });
  }

  function fillCorrection(component: DriversPage) {
    fillReview(component);
    component.identityCorrectionForm.patchValue({ currentPassword: 'Synthetic-ADMIN-password', dataEntryErrorConfirmed: true, samePersonConfirmed: true });
  }

  it('opens a restricted correction without recovering or prefilling any old identity or password', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityCorrection(driver);
    fixture.detectChanges();
    expect(fixture.componentInstance.identityReviewMode()).toBe('correction');
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.identityCorrectionForm.controls.currentPassword.value).toBe('');
    expect(fixture.nativeElement.querySelector('#driver-correction-password')?.type).toBe('password');
    expect(fixture.nativeElement.textContent).toContain('DNI correcto del documento original');
    expect(fixture.nativeElement.textContent).toContain('mismo titular');
    expect(fixture.nativeElement.textContent).not.toContain(driver.documentNumber);
    expect(fixture.nativeElement.querySelector('.management-page').hasAttribute('inert')).toBe(true);
    http.expectNone((request) => true);
  });

  it('requires ADMIN password and all human confirmations before correcting an identity', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityCorrection(driver);
    fillCorrection(fixture.componentInstance);
    for (const missing of ['currentPassword', 'dataEntryErrorConfirmed', 'samePersonConfirmed'] as const) {
      const control = fixture.componentInstance.identityCorrectionForm.controls[missing];
      const previous = control.value;
      if (missing === 'currentPassword') fixture.componentInstance.identityCorrectionForm.controls.currentPassword.setValue('');
      else fixture.componentInstance.identityCorrectionForm.controls[missing].setValue(false);
      fixture.componentInstance.submitIdentityReview();
      http.expectNone((request) => request.method === 'POST');
      if (missing === 'currentPassword') fixture.componentInstance.identityCorrectionForm.controls.currentPassword.setValue(previous as string);
      else fixture.componentInstance.identityCorrectionForm.controls[missing].setValue(previous as boolean);
    }
    fixture.componentInstance.identityReviewForm.controls.originalReviewed.setValue(false);
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.identityReviewForm.controls.originalReviewed.setValue(true);
    fixture.componentInstance.identityReviewForm.controls.identityConfirmed.setValue(false);
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((request) => request.method === 'POST');
  });

  it('sends one correction with reauthentication, clears DNI and password immediately, and keeps provisioning separate', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityCorrection(driver);
    fillCorrection(fixture.componentInstance);
    fixture.componentInstance.submitIdentityReview();
    const request = http.expectOne('/api/v1/drivers/driver-1/identity-review/corrections');
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(request.request.body).toEqual({ identityReview: { documentType: 'DNI', documentNumber: '00000002', method: 'IN_PERSON',
      reference: 'Cotejo sintético presencial', originalReviewed: true, identityConfirmed: true },
      reason: 'Regularización sintética autorizada.', currentPassword: 'Synthetic-ADMIN-password',
      operationKey: expect.stringMatching(/^[0-9a-f-]{36}$/), dataEntryErrorConfirmed: true, samePersonConfirmed: true });
    expect(fixture.componentInstance.identityReviewTarget()).toBeNull();
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.identityCorrectionForm.controls.currentPassword.value).toBe('');
    fixture.componentInstance.submitIdentityReview();
    fixture.componentInstance.viewDetails(driver);
    fixture.componentInstance.toggleActive(driver);
    http.expectNone((operation) => true);
    request.flush({ userId: 'account-1', identityVerified: true });
    http.expectOne((operation) => operation.url === '/api/v1/drivers').flush(page([driver]));
    fixture.detectChanges();
    expect(fixture.componentInstance.notice()).toContain('rectificación del DNI ingresado por error');
    expect(fixture.componentInstance.notice()).toContain('no aprueba documentos');
    expect(fixture.nativeElement.textContent).not.toContain('Synthetic-ADMIN-password');
    expect(fixture.nativeElement.textContent).not.toContain('00000002');
    http.expectNone((operation) => /regularization|approve|provision/.test(operation.url));
  });

  it.each([0, 400, 401, 403, 404, 409, 429])('handles correction failure %s without retaining credentials, echoing PII or retrying before a fresh read', (status) => {
    const fixture = create();
    fixture.componentInstance.startIdentityCorrection(driver);
    fillCorrection(fixture.componentInstance);
    fixture.componentInstance.submitIdentityReview();
    http.expectOne('/api/v1/drivers/driver-1/identity-review/corrections').flush({ message: 'PRIVATE DNI 00000002', currentPassword: 'Synthetic-ADMIN-password' }, { status, statusText: 'Not confirmed' });
    fixture.detectChanges();
    expect(fixture.componentInstance.identityReviewMustRefresh()).toBe(true);
    expect(fixture.componentInstance.identityCorrectionForm.controls.currentPassword.value).toBe('');
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
    expect(fixture.componentInstance.notice()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('PRIVATE DNI');
    expect(fixture.nativeElement.textContent).not.toContain('Synthetic-ADMIN-password');
    fixture.componentInstance.startIdentityCorrection(driver);
    fixture.componentInstance.submitIdentityReview();
    http.expectNone((operation) => operation.method === 'POST');
    if (status === 409) expect(fixture.componentInstance.error()?.message).toContain('el DNI debe coincidir con la ficha conservada');
    fixture.componentInstance.load();
    http.expectOne((operation) => operation.url === '/api/v1/drivers').flush(page([driver]));
    expect(fixture.componentInstance.identityReviewMustRefresh()).toBe(false);
    fixture.componentInstance.startIdentityCorrection(driver);
    expect(fixture.componentInstance.identityCorrectionForm.controls.currentPassword.value).toBe('');
  });

  it('clears correction credentials on close and navigation and refuses a different account response', () => {
    const fixture = create();
    fixture.componentInstance.startIdentityCorrection(driver);
    fillCorrection(fixture.componentInstance);
    fixture.componentInstance.closeIdentityReview();
    expect(fixture.componentInstance.identityCorrectionForm.controls.currentPassword.value).toBe('');
    fixture.componentInstance.startIdentityCorrection(driver);
    fillCorrection(fixture.componentInstance);
    fixture.componentInstance.submitIdentityReview();
    http.expectOne('/api/v1/drivers/driver-1/identity-review/corrections').flush({ userId: 'other-account', identityVerified: true });
    expect(fixture.componentInstance.identityReviewMustRefresh()).toBe(true);
    expect(fixture.componentInstance.notice()).toBeNull();
    fixture.componentInstance.identityCorrectionForm.controls.currentPassword.setValue('Synthetic-ADMIN-password');
    fixture.componentInstance.identityReviewForm.controls.documentNumber.setValue('00000002');
    fixture.destroy();
    expect(fixture.componentInstance.identityCorrectionForm.controls.currentPassword.value).toBe('');
    expect(fixture.componentInstance.identityReviewForm.controls.documentNumber.value).toBe('');
  });

  it('opens regularization with identifiers only and requires a reason and explicit preservation confirmation', () => {
    const fixture = create();
    fixture.componentInstance.startRegularization(driver);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Regularizar cuenta existente');
    expect(fixture.nativeElement.textContent).toContain('nunca datos supuestos');
    expect(fixture.nativeElement.textContent).not.toContain(driver.fullName);
    expect(fixture.nativeElement.textContent).not.toContain(driver.documentNumber);
    expect(fixture.nativeElement.querySelector('input[type="file"]')).toBeNull();
    fixture.componentInstance.submitRegularization();
    http.expectNone((request) => request.method === 'POST');
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.regularizationForm.controls.identityPreservedConfirmed.setValue(false);
    fixture.componentInstance.submitRegularization();
    http.expectNone((request) => request.method === 'POST');
    http.expectNone((request) => request.method === 'GET');
  });

  it('opens only a pending dossier without auto-approval, PII reads, account creation or duplicate submission', () => {
    const fixture = create();
    fixture.componentInstance.startRegularization(driver);
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.submitRegularization();
    const request = http.expectOne('/api/v1/drivers/driver-1/regularization');
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(request.request.body).toEqual({ operationKey: expect.stringMatching(/^[0-9a-f-]{36}$/),
      reason: 'Regularización sintética con conservación del historial.', identityPreservedConfirmed: true });
    expect(fixture.componentInstance.regularizationForm.controls.reason.value).toBe('');
    fixture.componentInstance.submitRegularization();
    fixture.componentInstance.closeRegularization();
    http.expectNone((item) => item.method === 'POST');
    expect(fixture.componentInstance.regularizationTarget()).not.toBeNull();
    request.flush({ requestId: 'legacy-request', status: 'PENDING_REVIEW', existingUserId: 'account-1', existingDriverId: 'driver-1' });
    fixture.detectChanges();
    expect(fixture.componentInstance.regularizationTarget()).toBeNull();
    expect(fixture.componentInstance.regularizationRequestId()).toBe('legacy-request');
    expect(fixture.componentInstance.notice()).toContain('no aprueba documentos');
    expect(fixture.nativeElement.textContent).not.toContain(driver.documentNumber);
    http.expectNone((item) => item.method === 'GET');
    http.expectNone((item) => /approve|provision|auth/.test(item.url));
  });

  it.each([0, 403, 409])('keeps the same operation on explicit retry after response %s and never echoes server PII', (status) => {
    const fixture = create();
    fixture.componentInstance.startRegularization(driver);
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.submitRegularization();
    const first = http.expectOne('/api/v1/drivers/driver-1/regularization');
    const originalBody = first.request.body;
    first.flush({ message: 'PRIVATE DNI 99999999', email: 'private@example.test' }, { status, statusText: 'Unconfirmed' });
    fixture.detectChanges();
    expect(fixture.componentInstance.regularizationUnconfirmed()).toBe(true);
    expect(fixture.componentInstance.notice()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('PRIVATE DNI');
    expect(fixture.nativeElement.textContent).not.toContain('private@example.test');
    fixture.componentInstance.submitRegularization();
    http.expectNone((item) => item.method === 'POST');
    fixture.componentInstance.retryRegularization();
    const retry = http.expectOne('/api/v1/drivers/driver-1/regularization');
    expect(retry.request.body).toEqual(originalBody);
    retry.flush({ requestId: 'legacy-request', status: 'PENDING_REVIEW', existingUserId: 'account-1', existingDriverId: 'driver-1' });
    expect(fixture.componentInstance.regularizationRequestId()).toBe('legacy-request');
  });

  it('does not claim a linked dossier when the server response identifies a different account', () => {
    const fixture = create();
    fixture.componentInstance.startRegularization(driver);
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.submitRegularization();
    http.expectOne('/api/v1/drivers/driver-1/regularization').flush({
      requestId: 'wrong-request', status: 'PENDING_REVIEW', existingUserId: 'other-account', existingDriverId: 'driver-1',
    });
    expect(fixture.componentInstance.regularizationUnconfirmed()).toBe(true);
    expect(fixture.componentInstance.regularizationRequestId()).toBeNull();
    expect(fixture.componentInstance.notice()).toBeNull();
  });

  it('keeps background actions inert and prevents a second operation during an uncertain regularization', () => {
    const fixture = create();
    fixture.componentInstance.startRegularization(driver);
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.submitRegularization();
    http.expectOne('/api/v1/drivers/driver-1/regularization').flush({}, { status: 0, statusText: 'Response lost' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.management-page').hasAttribute('inert')).toBe(true);
    fixture.componentInstance.startRegularization({ ...driver, id: 'other-driver', userId: 'other-user' });
    fixture.componentInstance.startIdentityReview(driver);
    fixture.componentInstance.viewDetails(driver);
    fixture.componentInstance.toggleActive(driver);
    fixture.componentInstance.confirmPendingAction();
    fixture.componentInstance.linkVehicle();
    fixture.componentInstance.markPrimary('vehicle-1');
    fixture.componentInstance.unlinkVehicle('vehicle-1', 'TEST-001');
    expect(fixture.componentInstance.regularizationTarget()).toEqual({ driverId: 'driver-1', userId: 'account-1' });
    expect(fixture.componentInstance.identityReviewTarget()).toBeNull();
    expect(fixture.componentInstance.pendingAction()).toBeNull();
    http.expectNone((request) => true);
    fixture.componentInstance.closeRegularization();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.management-page').hasAttribute('inert')).toBe(false);
  });

  it('recovers the pending dossier after reloading the driver detail without offering or posting another opening', () => {
    const fixture = create();
    const pendingState = {
      driverId: driver.id, accountId: driver.userId, requestId: 'pending-legacy-request', incorporationStatus: 'PENDING_REVIEW',
      personalApproved: false, accountProvisioned: false, accountEnabled: true, identityReviewed: true,
      groupActive: true, driverEnabled: false, personalDocumentsStatus: 'UNKNOWN', activeVehicleLinks: 0,
      unitCardinalitySatisfied: true, operationalEnabled: false,
      blockers: ['PERSONAL_APPROVAL_MISSING', 'UNIT_AND_SERVICE_REVIEW_PENDING'], observedAt: '2026-10-23T12:00:00Z',
    };
    for (let visit = 0; visit < 2; visit++) {
      fixture.componentInstance.viewDetails(driver);
      http.expectOne('/api/v1/drivers/driver-1').flush({ driver, vehicles: [] });
      http.expectOne('/api/v1/drivers/driver-1/incorporation-status').flush(pendingState);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Expediente registrado: pending-legacy-request');
      expect(fixture.nativeElement.textContent).toContain('Consultar este expediente en Solicitudes');
      expect(fixture.nativeElement.textContent).not.toContain('Abrir expediente de regularización');
      fixture.componentInstance.startRegularization(driver);
      fixture.componentInstance.submitRegularization();
      expect(fixture.componentInstance.regularizationTarget()).toBeNull();
      http.expectNone((request) => request.method === 'POST');
      fixture.componentInstance.closeDetails();
    }
  });

  it('restricts regularization to ADMIN with a linked account and clears the form on cancel and navigation', () => {
    allowedAdmin.set(false);
    const fixture = create();
    fixture.componentInstance.startRegularization(driver);
    expect(fixture.componentInstance.regularizationTarget()).toBeNull();
    allowedAdmin.set(true);
    fixture.componentInstance.startRegularization({ ...driver, userId: null });
    expect(fixture.componentInstance.regularizationTarget()).toBeNull();
    fixture.componentInstance.startRegularization(driver);
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.closeRegularization();
    expect(fixture.componentInstance.regularizationForm.controls.reason.value).toBe('');
    fixture.componentInstance.startRegularization(driver);
    fillRegularization(fixture.componentInstance);
    fixture.componentInstance.submitRegularization();
    const request = http.expectOne('/api/v1/drivers/driver-1/regularization');
    fixture.destroy();
    expect(request.cancelled).toBe(true);
    expect(fixture.componentInstance.regularizationTarget()).toBeNull();
  });

  function page(items: readonly unknown[]) { return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }; }
});
