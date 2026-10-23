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

  function page(items: readonly unknown[]) { return { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 }; }
});
