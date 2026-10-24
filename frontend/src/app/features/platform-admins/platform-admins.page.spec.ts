import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SKIP_AUTH_REFRESH } from '../../core/auth/auth-http-context';
import { PlatformAdmin } from '../../core/platform-admins/platform-admins-api.service';
import { PlatformAdminsPage } from './platform-admins.page';

describe('PlatformAdminsPage', () => {
  let http: HttpTestingController;
  const base = '/api/v1/platform/admins';
  const admin: PlatformAdmin = { adminUserId: 'admin-1', organizationId: 'org-1', active: true,
    activationId: null, status: 'NO_ACTIVATION', replacesAdminUserId: null, credentialExpiresAt: null, createdAt: '2026-10-15T12:00:00Z', identityVerified: true };
  const credential = { adminUserId: 'admin-new', organizationId: 'org-1', activationId: 'activation-1',
    status: 'PENDING_CLAIM', credential: 'synthetic-once-only', credentialExpiresAt: '2026-10-16T12:00:00Z' };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PlatformAdminsPage], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); });
  function create() {
    const fixture = TestBed.createComponent(PlatformAdminsPage);
    http.expectOne('/api/v1/identity/status').flush({ userId: 'owner-1', identityVerified: true });
    http.expectOne(base).flush([{ ...admin, email: 'private@example.test', fullName: 'Persona Sintética' }]);
    fixture.detectChanges();
    return fixture;
  }
  function fillIdentity(component: PlatformAdminsPage) {
    component.form.patchValue({ email: 'new@example.test', fullName: 'Identidad Sintética', reason: 'Alta autorizada académica.',
      identityDocumentNumber: '00000001', identityReviewMethod: 'IN_PERSON', identityReviewReference: 'Cotejo sintético uno',
      originalReviewed: true, identityConfirmed: true });
  }
  const identityReview = { documentType: 'DNI', documentNumber: '00000001', method: 'IN_PERSON',
    reference: 'Cotejo sintético uno', originalReviewed: true, identityConfirmed: true };

  it('lists only account and organization references, never PII supplied in a response', () => {
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('CUENTA-admin-1');
    expect(fixture.nativeElement.textContent).not.toContain('private@example.test');
    expect(fixture.nativeElement.textContent).not.toContain('Persona Sintética');
    expect(fixture.nativeElement.textContent).toContain('cuatro altas ADMIN acumuladas');
  });

  it('requires a valid create form and clears identity after confirmed credential issuance', () => {
    const fixture = create();
    fixture.componentInstance.open('create');
    http.expectOne((request) => request.url === '/api/v1/organizations').flush({ items: [
      { id: 'org-1', status: 'ACTIVE', legalName: 'Referencia ficticia' }, { id: 'org-2', status: 'SUSPENDED', legalName: 'Referencia suspendida' },
    ], page: 0, size: 100, totalItems: 2, totalPages: 1 });
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
    expect(fixture.componentInstance.organizations().map((org) => org.id)).toEqual(['org-1']);
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.controls.organizationId.setValue('org-1');
    fixture.componentInstance.submit();
    const request = http.expectOne(base);
    expect(request.request.body).toEqual({ organizationId: 'org-1', email: 'new@example.test', fullName: 'Identidad Sintética', reason: 'Alta autorizada académica.', identityReview });
    expect(fixture.componentInstance.form.controls.identityDocumentNumber.value).toBe('');
    expect(fixture.componentInstance.form.controls.email.value).toBe('');
    request.flush(credential);
    http.expectOne(base).flush([{ ...admin, ...credential, active: false }]);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.controls.email.value).toBe('');
    expect(fixture.componentInstance.form.controls.fullName.value).toBe('');
    expect(fixture.nativeElement.textContent).toContain('synthetic-once-only');
    expect(fixture.nativeElement.textContent).not.toContain('Identidad Sintética');
    fixture.componentInstance.hideCredential();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('synthetic-once-only');
  });

  it('requires explicit identity and replacement confirmations without accepting them by default', () => {
    const fixture = create();
    fixture.componentInstance.open('replace', admin);
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.controls.verificationReference.setValue('Acta sintética del cotejo presencial');
    expect(fixture.componentInstance.form.controls.identityChecked.value).toBe(false);
    expect(fixture.componentInstance.form.controls.replacementAuthorized.value).toBe(false);
    fixture.componentInstance.submit();
    http.expectNone(`${base}/admin-1/replacement`);
    fixture.componentInstance.form.patchValue({ identityChecked: true, replacementAuthorized: true, verificationMethod: 'IN_PERSON' });
    fixture.componentInstance.submit();
    const request = http.expectOne(`${base}/admin-1/replacement`);
    expect(request.request.body.identityChecked).toBe(true);
    expect(request.request.body.replacementAuthorized).toBe(true);
    expect(request.request.body.verificationMethod).toBe('IN_PERSON');
    expect(request.request.body.identityReview).toEqual(identityReview);
    request.flush(credential);
    http.expectOne(base).flush([admin, { ...admin, ...credential, active: false, replacesAdminUserId: 'admin-1' }]);
    expect(fixture.componentInstance.admins()[0].active).toBe(true);
    expect(fixture.componentInstance.admins()[1].active).toBe(false);
  });

  it.each([0, 409])('blocks another mutation after unconfirmed operation %s until a fresh list read', (status) => {
    const fixture = create();
    fixture.componentInstance.open('replace', admin);
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.patchValue({ verificationReference: 'Referencia sintética', identityChecked: true, replacementAuthorized: true });
    fixture.componentInstance.submit();
    http.expectOne(`${base}/admin-1/replacement`).flush({}, { status, statusText: 'Unconfirmed' });
    expect(fixture.componentInstance.mustRefresh()).toBe(true);
    expect(fixture.componentInstance.credential()).toBeNull();
    expect(fixture.componentInstance.form.controls.email.value).toBe('');
    fixture.componentInstance.open('replace', admin);
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
    expect(fixture.componentInstance.admins()).toHaveLength(1);
    expect(fixture.componentInstance.admins()[0].active).toBe(true);
    fixture.componentInstance.load();
    http.expectOne('/api/v1/identity/status').flush({ userId: 'owner-1', identityVerified: true });
    http.expectOne(base).flush([admin]);
    expect(fixture.componentInstance.mustRefresh()).toBe(false);
  });

  it('renews a credential without identity fields and removes it on navigation', () => {
    const fixture = create();
    fixture.componentInstance.open('rotate', { ...admin, active: false, status: 'PENDING_CLAIM' });
    fixture.componentInstance.form.controls.reason.setValue('Renovación autorizada de credencial.');
    fixture.componentInstance.submit();
    const request = http.expectOne(`${base}/admin-1/activation-credential`);
    expect(request.request.body).toEqual({ reason: 'Renovación autorizada de credencial.' });
    request.flush(credential);
    http.expectOne(base).flush([admin]);
    expect(fixture.componentInstance.credential()).not.toBeNull();
    fixture.destroy();
    expect(fixture.componentInstance.credential()).toBeNull();
  });

  it('requires a reason and explicit confirmation before immediate revocation', () => {
    const fixture = create();
    fixture.componentInstance.open('revoke', admin);
    expect(fixture.componentInstance.form.controls.revocationConfirmed.value).toBe(false);
    fixture.componentInstance.form.controls.reason.setValue('Retiro de acceso autorizado por seguridad.');
    fixture.componentInstance.submit();
    http.expectNone(`${base}/admin-1/revoke`);
    fixture.componentInstance.form.controls.revocationConfirmed.setValue(true);
    fixture.componentInstance.submit();
    const request = http.expectOne(`${base}/admin-1/revoke`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Retiro de acceso autorizado por seguridad.' });
    request.flush({ ...admin, active: false });
    http.expectOne(base).flush([{ ...admin, active: false }]);
    expect(fixture.componentInstance.credential()).toBeNull();
    expect(fixture.componentInstance.admins()[0].active).toBe(false);
    expect(fixture.componentInstance.form.controls.reason.value).toBe('');
    expect(fixture.componentInstance.form.controls.revocationConfirmed.value).toBe(false);
  });

  it('allows preparing a replacement after the outgoing account was already revoked', () => {
    const fixture = create();
    fixture.componentInstance.admins.set([{ ...admin, active: false }]);
    fixture.detectChanges();
    const replacement = Array.from(fixture.nativeElement.querySelectorAll('tbody button'))
      .find((element) => (element as HTMLButtonElement).textContent?.includes('Preparar reemplazo')) as HTMLButtonElement;
    expect(replacement).toBeDefined();
    replacement.click();
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.patchValue({ verificationReference: 'Cotejo presencial sintético', identityChecked: true, replacementAuthorized: true });
    fixture.componentInstance.submit();
    const request = http.expectOne(`${base}/admin-1/replacement`);
    request.flush(credential);
    http.expectOne(base).flush([{ ...admin, active: false }, { ...admin, ...credential, active: false }]);
    expect(fixture.componentInstance.credential()?.adminUserId).toBe('admin-new');
  });

  it('does not announce or retry revocation after a lost response and consults before continuing', () => {
    const fixture = create();
    fixture.componentInstance.open('revoke', admin);
    fixture.componentInstance.form.patchValue({ reason: 'Revocación autorizada.', revocationConfirmed: true });
    fixture.componentInstance.submit();
    http.expectOne(`${base}/admin-1/revoke`).flush({}, { status: 0, statusText: 'Network unavailable' });
    expect(fixture.componentInstance.notice()).toBeNull();
    expect(fixture.componentInstance.mustRefresh()).toBe(true);
    expect(fixture.componentInstance.admins()[0].active).toBe(true);
    fixture.componentInstance.submit();
    http.expectNone(`${base}/admin-1/revoke`);
    fixture.componentInstance.load();
    http.expectOne('/api/v1/identity/status').flush({ userId: 'owner-1', identityVerified: true });
    http.expectOne(base).flush([{ ...admin, active: false }]);
    expect(fixture.componentInstance.admins()[0].active).toBe(false);
    expect(fixture.componentInstance.mustRefresh()).toBe(false);
  });

  it('requires the original review and identity confirmation explicitly, including an eight digit DNI', () => {
    const fixture = create();
    expect(fixture.nativeElement.querySelector('#admin-identity-document')).toBeNull();
    fixture.componentInstance.open('replace', admin);
    expect(fixture.componentInstance.form.controls.originalReviewed.value).toBe(false);
    expect(fixture.componentInstance.form.controls.identityConfirmed.value).toBe(false);
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.patchValue({ verificationReference: 'Acta sintética', identityChecked: true, replacementAuthorized: true,
      originalReviewed: false });
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.form.patchValue({ originalReviewed: true, identityConfirmed: false });
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.form.patchValue({ identityConfirmed: true, identityDocumentNumber: '0000001' });
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.form.controls.identityDocumentNumber.setValue('0000A001');
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
    fixture.componentInstance.closeForm();
  });

  it('clears the restricted review and password on cancel and navigation without sending them', () => {
    const fixture = create();
    fixture.componentInstance.open('replace', admin);
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.closeForm();
    expect(fixture.componentInstance.form.controls.identityDocumentNumber.value).toBe('');
    expect(fixture.componentInstance.form.controls.identityReviewReference.value).toBe('');
    expect(fixture.componentInstance.form.controls.originalReviewed.value).toBe(false);
    expect(fixture.componentInstance.form.controls.identityConfirmed.value).toBe(false);
    fixture.componentInstance.open('replace', admin);
    fillIdentity(fixture.componentInstance);
    fixture.destroy();
    expect(fixture.componentInstance.form.controls.identityDocumentNumber.value).toBe('');
    expect(fixture.componentInstance.form.controls.fullName.value).toBe('');
    expect(fixture.componentInstance.form.controls.currentPassword.value).toBe('');
    http.expectNone((request) => request.method === 'POST');
  });

  it('guides the unregistered owner through explicit restricted self enrollment before allowing ADMIN management', () => {
    const fixture = TestBed.createComponent(PlatformAdminsPage);
    http.expectOne('/api/v1/identity/status').flush({ userId: 'owner-1', identityVerified: false });
    http.expectNone(base);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Identidad del titular pendiente');
    fixture.componentInstance.open('create');
    expect(fixture.componentInstance.formMode()).toBeNull();
    fixture.componentInstance.open('owner');
    expect(fixture.componentInstance.form.controls.ownerDeclaration.value).toBe(false);
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.controls.currentPassword.setValue('Synthetic-owner-password');
    fixture.componentInstance.submit();
    http.expectNone('/api/v1/platform/identity/self-enrollment');
    fixture.componentInstance.form.controls.ownerDeclaration.setValue(true);
    fixture.componentInstance.submit();
    const request = http.expectOne('/api/v1/platform/identity/self-enrollment');
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(request.request.body).toEqual({ currentPassword: 'Synthetic-owner-password', ownerDeclaration: true, identityReview });
    expect(fixture.componentInstance.form.controls.currentPassword.value).toBe('');
    expect(fixture.componentInstance.form.controls.identityDocumentNumber.value).toBe('');
    request.flush({ userId: 'owner-1', identityVerified: true });
    http.expectOne(base).flush([admin]);
    fixture.detectChanges();
    expect(fixture.componentInstance.identityStatus()?.identityVerified).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('No constituye una verificación por un tercero');
    expect(fixture.nativeElement.textContent).not.toContain('00000001');
    expect(fixture.nativeElement.textContent).not.toContain('Synthetic-owner-password');
    expect(fixture.componentInstance.credential()).toBeNull();
  });

  it('clears owner credentials after a rejected review and requires a status read without retrying', () => {
    const fixture = TestBed.createComponent(PlatformAdminsPage);
    http.expectOne('/api/v1/identity/status').flush({ userId: 'owner-1', identityVerified: false });
    fixture.componentInstance.open('owner');
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.form.patchValue({ currentPassword: 'Synthetic-wrong-password', ownerDeclaration: true });
    fixture.componentInstance.submit();
    http.expectOne('/api/v1/platform/identity/self-enrollment').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(fixture.componentInstance.form.controls.currentPassword.value).toBe('');
    expect(fixture.componentInstance.form.controls.identityDocumentNumber.value).toBe('');
    expect(fixture.componentInstance.mustRefresh()).toBe(true);
    expect(fixture.componentInstance.identityStatus()?.identityVerified).toBe(false);
    fixture.componentInstance.submit();
    http.expectNone((request) => request.method === 'POST');
  });

  it('reviews an existing ADMIN with a restricted input and returns only the boolean identity status to the list', () => {
    const fixture = create();
    fixture.componentInstance.admins.set([{ ...admin, identityVerified: false }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Revisar identidad administrativa');
    fixture.componentInstance.open('review', { ...admin, identityVerified: false });
    fillIdentity(fixture.componentInstance);
    fixture.componentInstance.submit();
    const request = http.expectOne(`${base}/admin-1/identity-review`);
    expect(request.request.body).toEqual({ identityReview, reason: 'Alta autorizada académica.' });
    expect(request.request.context.get(SKIP_AUTH_REFRESH)).toBe(true);
    expect(fixture.componentInstance.form.controls.identityDocumentNumber.value).toBe('');
    request.flush({ userId: 'admin-1', identityVerified: true });
    http.expectOne(base).flush([admin]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('00000001');
    expect(fixture.nativeElement.textContent).not.toContain('Identidad Sintética');
    expect(fixture.nativeElement.textContent).toContain('Comprobada');
    expect(fixture.componentInstance.credential()).toBeNull();
  });
});
