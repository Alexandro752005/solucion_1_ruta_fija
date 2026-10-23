import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminActivationApiService } from '../../core/admin-activation/admin-activation-api.service';
import { AdminActivationPage } from './admin-activation.page';

const base = '/api/v1/admin-activation';
const document = {
  documentKey: 'SYNTHETIC_ADMIN_TERMS', version: 'SYNTHETIC-0.0.1',
  content: 'DOCUMENTO SINTÉTICO DE PRUEBA. Sin aceptación real.', contentSha256: 'a'.repeat(64),
  synthetic: true, accepted: false, acceptedAt: null,
};

describe('AdminActivationPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdminActivationPage>;
  let page: AdminActivationPage;
  const interceptor = vi.fn(() => { throw new Error('No debe usar sesión CRM.'); });

  beforeEach(async () => {
    interceptor.mockClear();
    await TestBed.configureTestingModule({
      imports: [AdminActivationPage],
      providers: [provideHttpClient(withInterceptors([interceptor])), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminActivationPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function claim(): void {
    page.claimForm.setValue({ credential: 'ac1.SYNTHETIC', password: 'Synthetic-Password-2026', confirmation: 'Synthetic-Password-2026' });
    page.claim();
    const request = http.expectOne(`${base}/claim`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ credential: 'ac1.SYNTHETIC', newPassword: 'Synthetic-Password-2026' });
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.withCredentials).toBe(false);
    expect(request.request.credentials).toBe('omit');
    request.flush({ activationId: 'activation-synthetic', activationToken: 'aa1.SYNTHETIC',
      expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(), status: 'IN_PROGRESS' });
  }

  function begin(): void {
    page.beginOtp();
    const request = http.expectOne(`${base}/otp/begin`);
    expect(request.request.headers.get('X-Admin-Activation-Token')).toBe('aa1.SYNTHETIC');
    request.flush({ verificationToken: 'SYNTHETIC-otp-token', otpExpiresAt: new Date(Date.now() + 300_000).toISOString(),
      challengeExpiresAt: new Date(Date.now() + 1_800_000).toISOString(), emailSent: true });
  }

  function verify(): void {
    page.otpForm.controls.code.setValue('123456');
    page.verifyOtp();
    const request = http.expectOne(`${base}/otp/verify`);
    expect(request.request.body).toEqual({ verificationToken: 'SYNTHETIC-otp-token', code: '123456' });
    expect(page.otpForm.controls.code.value).toBe('');
    request.flush({ activationId: 'activation-synthetic', adminUserId: 'admin-synthetic', organizationId: 'org-synthetic',
      status: 'IN_PROGRESS', passwordSet: true, emailVerified: true, termsAccepted: false,
      expiresAt: new Date(Date.now() + 1_800_000).toISOString() });
  }

  function readTerms(): void {
    page.loadTerms();
    http.expectOne(`${base}/terms`).flush(document);
    fixture.detectChanges();
  }

  it('no inicia red y valida contraseña/confirmación antes de reclamar', () => {
    page.claim();
    http.expectNone(`${base}/claim`);
    page.claimForm.setValue({ credential: 'ac1.SYNTHETIC', password: 'Synthetic-Password-2026', confirmation: 'otra' });
    page.claim();
    expect(page.error()).toContain('no coinciden');
    http.expectNone(`${base}/claim`);
  });

  it('registra contraseña sin sesión CRM, borra secretos y no envía OTP automáticamente', () => {
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    claim();
    expect(page.claimForm.getRawValue()).toEqual({ credential: '', password: '', confirmation: '' });
    expect(page.claimed()).toBe(true);
    expect(interceptor).not.toHaveBeenCalled();
    expect(storage).not.toHaveBeenCalled();
    http.expectNone(`${base}/otp/begin`);
    http.expectNone('/api/v1/auth/login');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('aa1.SYNTHETIC');
  });

  it('limpia credencial y contraseña también ante error y no reintenta', () => {
    page.claimForm.setValue({ credential: 'ac1.SYNTHETIC', password: 'Synthetic-Password-2026', confirmation: 'Synthetic-Password-2026' });
    page.claim();
    http.expectOne(`${base}/claim`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(page.claimForm.getRawValue()).toEqual({ credential: '', password: '', confirmation: '' });
    expect(page.error()).toContain('dejó de ser válida');
    http.expectNone(`${base}/claim`);
    http.expectNone('/api/v1/auth/refresh');
  });

  it('envía OTP solo por acción expresa, limpia código y no expone token en HTML', () => {
    claim(); begin();
    page.otpForm.controls.code.setValue('12345');
    page.verifyOtp();
    http.expectNone(`${base}/otp/verify`);
    verify();
    expect(page.verified()).toBe(true);
    expect(page.otp()).toBe(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('SYNTHETIC-otp-token');
    http.expectNone(`${base}/terms`);
  });

  it('conserva verificación ante fallo de proveedor y nunca reintenta el envío', () => {
    claim();
    page.beginOtp();
    http.expectOne(`${base}/otp/begin`).flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.error()).toContain('No se reintentará automáticamente');
    expect(page.claimed()).toBe(true);
    http.expectNone(`${base}/otp/begin`);
  });

  it('reenvía la credencial de verificación previa y muestra límite temporal sin reintentos', () => {
    claim(); begin();
    page.resendOtp();
    const request = http.expectOne(`${base}/otp/resend`);
    expect(request.request.body).toEqual({ verificationToken: 'SYNTHETIC-otp-token' });
    request.flush({}, { status: 429, statusText: 'Too Many Requests' });
    expect(page.error()).toContain('límite temporal');
    http.expectNone(`${base}/otp/resend`);
  });

  it('impide consultar términos y completar antes de verificar correo', () => {
    claim();
    page.loadTerms(); page.complete();
    http.expectNone(`${base}/terms`);
    http.expectNone(`${base}/complete`);
  });

  it('no permite aceptar ni habilita la cuenta cuando no hay documento aprobado', () => {
    claim(); begin(); verify();
    page.loadTerms();
    http.expectOne(`${base}/terms`).flush({}, { status: 404, statusText: 'Not Found' });
    expect(page.error()).toContain('No hay términos aprobados');
    expect(page.terms()).toBe(null);
    page.termsForm.controls.accepted.setValue(true);
    page.acceptTerms(); page.complete();
    http.expectNone(`${base}/terms`);
    http.expectNone(`${base}/complete`);
  });

  it('exige aceptación expresa de versión/huella sintéticas y completa sin autenticación automática', () => {
    claim(); begin(); verify(); readTerms();
    expect(page.termsForm.controls.accepted.value).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('DOCUMENTO SINTÉTICO DE PRUEBA');
    page.acceptTerms();
    http.expectNone((request) => request.method === 'POST' && request.url === `${base}/terms`);
    page.termsForm.controls.accepted.setValue(true);
    page.acceptTerms();
    const acceptance = http.expectOne(`${base}/terms`);
    expect(acceptance.request.body).toEqual({ version: document.version, contentSha256: document.contentSha256, accepted: true });
    acceptance.flush({ acceptanceId: 'acceptance-synthetic', adminUserId: 'admin-synthetic', organizationId: 'org-synthetic',
      documentVersion: document.version, contentSha256: document.contentSha256, synthetic: true, acceptedAt: new Date().toISOString() });
    http.expectNone(`${base}/complete`);
    page.complete();
    http.expectOne(`${base}/complete`).flush({ activationId: 'activation-synthetic', status: 'COMPLETED', passwordSet: true,
      emailVerified: true, termsAccepted: true, adminUserId: 'admin-synthetic', organizationId: 'org-synthetic', expiresAt: new Date().toISOString() });
    expect(page.completed()).toBe(true);
    expect(page.terms()).toBe(null);
    expect(page.notice()).toContain('Vuelve al inicio de sesión');
    http.expectNone('/api/v1/auth/login');
    page.complete();
    http.expectNone(`${base}/complete`);
  });

  it('descarta documento y casilla si cambió la versión durante la aceptación', () => {
    claim(); begin(); verify(); readTerms();
    page.termsForm.controls.accepted.setValue(true);
    page.acceptTerms();
    http.expectOne(`${base}/terms`).flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.error()).toContain('versión del documento cambió');
    expect(page.terms()).toBe(null);
    expect(page.termsForm.controls.accepted.value).toBe(false);
    expect(page.accepted()).toBe(false);
    page.complete();
    http.expectNone(`${base}/complete`);
  });

  it('borra credenciales al destruir la pantalla y no conserva acceso utilizable', () => {
    claim(); begin();
    page.ngOnDestroy();
    expect(page.otp()).toBe(null);
    page.resendOtp(); page.beginOtp();
    http.expectNone(`${base}/otp/resend`); http.expectNone(`${base}/otp/begin`);
  });

  it('rechaza sesión devuelta ya vencida antes de mandar un OTP', () => {
    page.claimForm.setValue({ credential: 'ac1.SYNTHETIC', password: 'Synthetic-Password-2026', confirmation: 'Synthetic-Password-2026' });
    page.claim();
    http.expectOne(`${base}/claim`).flush({ activationId: 'activation-synthetic', activationToken: 'aa1.SYNTHETIC',
      expiresAt: new Date(Date.now() - 1).toISOString(), status: 'IN_PROGRESS' });
    expect(page.error()).toContain('venció');
    page.beginOtp();
    http.expectNone(`${base}/otp/begin`);
  });

  it('bloquea nuevas operaciones en el límite absoluto de treinta minutos', () => {
    const startedAt = Date.now();
    vi.spyOn(Date, 'now').mockReturnValue(startedAt);
    claim(); begin();
    vi.spyOn(Date, 'now').mockReturnValue(startedAt + 1_800_000);
    page.resendOtp();
    expect(page.expired()).toBe(true);
    expect(page.otp()).toBe(null);
    http.expectNone(`${base}/otp/resend`);
  });

  it('cancela una petición pendiente al salir y limpia el código', () => {
    claim(); begin();
    page.otpForm.controls.code.setValue('123456');
    page.verifyOtp();
    const pending = http.expectOne(`${base}/otp/verify`);
    page.ngOnDestroy();
    expect(pending.cancelled).toBe(true);
    expect(page.otpForm.controls.code.value).toBe('');
    expect(page.otp()).toBe(null);
  });

  it('consulta estado con header dedicado y sin interceptor de sesión', () => {
    TestBed.inject(AdminActivationApiService).status('aa1.SYNTHETIC').subscribe();
    const request = http.expectOne(`${base}/status`);
    expect(request.request.headers.get('X-Admin-Activation-Token')).toBe('aa1.SYNTHETIC');
    expect(request.request.withCredentials).toBe(false);
    expect(request.request.credentials).toBe('omit');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
    expect(interceptor).not.toHaveBeenCalled();
  });
});
