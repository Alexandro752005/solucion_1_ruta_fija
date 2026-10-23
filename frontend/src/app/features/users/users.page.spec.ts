import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagedUser } from '../../core/management/management.models';
import { UsersPage } from './users.page';

describe('UsersPage privacy and onboarding policy', () => {
  let http: HttpTestingController;
  const account: ManagedUser = { id: 'account-1', organizationId: 'org-1', role: 'CONDUCTOR',
    fullName: 'Persona Sintética', email: 'synthetic@example.test', phone: '900000001', active: true,
    createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [UsersPage], providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthSessionService, useValue: { user: signal(null), hasAnyRole: () => true } },
    ] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); TestBed.resetTestingModule(); });

  function create() {
    const fixture = TestBed.createComponent(UsersPage);
    http.expectOne((request) => request.url === '/api/v1/users').flush({ items: [account], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    fixture.detectChanges();
    return fixture;
  }

  it('uses account codes even if a response contains PII and directs incorporation to requests', () => {
    const fixture = create();
    const content = fixture.nativeElement.textContent as string;
    expect(content).toContain('CUENTA-account-1');
    for (const privateValue of [account.fullName, account.email, account.phone!]) expect(content).not.toContain(privateValue);
    expect(fixture.nativeElement.querySelector('a[href="/onboarding-requests"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#user-full-name')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="search"]')).toBeNull();
  });

  it('blocks legacy handlers without posting or patching conductor identity', () => {
    const fixture = create();
    fixture.componentInstance.startCreate();
    fixture.componentInstance.startEdit(account);
    fixture.componentInstance.form.setValue({ email: account.email, fullName: account.fullName, phone: account.phone!, password: 'synthetic-password', role: 'CONDUCTOR' });
    fixture.componentInstance.submit();
    expect(fixture.componentInstance.formOpen()).toBe(false);
    expect(fixture.componentInstance.form.controls.fullName.value).toBe('');
    http.expectNone((request) => request.method !== 'GET');
  });

  it('preserves assisted recovery and clears temporary inputs on close', () => {
    const fixture = create();
    fixture.componentInstance.startRecovery(account);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#recovery-new-email')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain(account.email);
    fixture.componentInstance.recoveryForm.setValue({ newEmail: 'new@example.test', evidenceMethod: 'IN_PERSON',
      evidenceReference: 'Cotejo presencial sintético', reason: 'Recuperación de acceso', expedienteReviewed: true, independentEvidenceConfirmed: true });
    fixture.componentInstance.submitRecovery();
    const recovery = http.expectOne('/api/v1/users/account-1/conductor-recovery');
    expect(recovery.request.method).toBe('POST');
    expect(recovery.request.body.passwordResetRequired).toBe(true);
    recovery.flush({ recoveryId: 'recovery-1', status: 'PENDING_EMAIL_VERIFICATION', verificationToken: 'synthetic-once',
      otpExpiresAt: '2026-10-15T12:05:00Z', emailSent: false, passwordResetRequired: true });
    fixture.componentInstance.closeRecovery();
    fixture.detectChanges();
    expect(fixture.componentInstance.recoveryStarted()).toBeNull();
    expect(fixture.componentInstance.recoveryForm.controls.newEmail.value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('synthetic-once');
  });
});
