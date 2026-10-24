import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AuthSessionService } from '../../core/auth/auth-session.service';
import { SessionUser } from '../../core/auth/auth.models';
import { InvitationsPage } from './invitations.page';

describe('InvitationsPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InvitationsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthSessionService,
          useValue: { user: signal<SessionUser | null>(null).asReadonly() },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('emite tras validar el correo, muestra el código una vez y nunca lo muestra en el historial', () => {
    const fixture = TestBed.createComponent(InvitationsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations?page=0&size=20').flush(emptyPage());
    fixture.detectChanges();

    const email = fixture.nativeElement.querySelector('#invitation-recipient-email') as HTMLInputElement;
    email.value = 'driver@example.test';
    email.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.invitation-form') as HTMLFormElement)
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();

    const issueRequest = http.expectOne('/api/v1/invitations');
    expect(issueRequest.request.method).toBe('POST');
    expect(issueRequest.request.body).toEqual({ recipientEmail: 'driver@example.test' });
    issueRequest.flush({
      id: 'invitation-1',
      recipientEmail: 'driver@example.test',
      code: '482731',
      issuedAt: '2026-10-14T12:00:00Z',
      expiresAt: '2026-10-19T12:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('482731');
    expect(fixture.nativeElement.textContent).toContain('fuera de Ruta Fija');
    expect(fixture.nativeElement.textContent).toContain('no lo envía por correo');
    http.expectOne('/api/v1/invitations?page=0&size=20').flush({
      ...emptyPage(),
      items: [summary()],
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const hideButton = Array.from(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => (button as HTMLButtonElement).textContent?.includes('Ocultar código')) as HTMLButtonElement;
    hideButton.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('482731');
    expect(fixture.nativeElement.textContent).not.toContain('driver@example.test');
    expect(fixture.nativeElement.textContent).toContain('invitation-1');
    expect(fixture.nativeElement.textContent).not.toContain('Aceptar invitación');
    expect(fixture.nativeElement.textContent).not.toContain('Código de invitación</th>');
  });

  it('no emite si el correo no pasa la validación de formulario', () => {
    const fixture = TestBed.createComponent(InvitationsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations?page=0&size=20').flush(emptyPage());
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.invitation-form') as HTMLFormElement)
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ingresa un correo válido');
    http.expectNone('/api/v1/invitations', 'No debe enviar una emisión inválida');
  });

  it('confirma la revocación y refresca el resumen sin código', () => {
    const fixture = TestBed.createComponent(InvitationsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations?page=0&size=20').flush({
      ...emptyPage(),
      items: [summary()],
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('tbody button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Revocar invitación');

    (fixture.nativeElement.querySelector('[role="dialog"] button.danger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const revokeRequest = http.expectOne('/api/v1/invitations/invitation-1/revoke');
    expect(revokeRequest.request.method).toBe('POST');
    expect(revokeRequest.request.body).toEqual({});
    revokeRequest.flush({ ...summary(), revokedAt: '2026-10-14T12:30:00Z' });
    fixture.detectChanges();

    http.expectOne('/api/v1/invitations?page=0&size=20').flush({
      ...emptyPage(),
      items: [{ ...summary(), revokedAt: '2026-10-14T12:30:00Z' }],
      totalItems: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Invitación revocada');
    expect(fixture.nativeElement.querySelector('tbody button')).toBeNull();
  });

  it('muestra carga vacía y permite reintentar errores del historial', () => {
    const fixture = TestBed.createComponent(InvitationsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations?page=0&size=20').flush(
      { status: 503, code: 'SERVICE_UNAVAILABLE', message: 'Temporalmente no disponible' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Temporalmente no disponible');

    (fixture.nativeElement.querySelector('button.management-button.compact') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne('/api/v1/invitations?page=0&size=20').flush(emptyPage());
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay invitaciones');
  });

  function emptyPage() {
    return { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 };
  }

  function summary() {
    return {
      id: 'invitation-1',
      recipientEmail: 'driver@example.test',
      issuedAt: '2026-10-14T12:00:00Z',
      expiresAt: '2026-10-19T12:00:00Z',
      revokedAt: null,
      usedAt: null,
    };
  }
});
