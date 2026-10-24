import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AdminTermsPage } from './admin-terms.page';

describe('AdminTermsPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTermsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('muestra versión y SHA-256, inicia sin aceptar y mantiene deshabilitada la acción', () => {
    const fixture = TestBed.createComponent(AdminTermsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/terms/current').flush(publishedDocument());
    fixture.detectChanges();

    const checkbox = fixture.nativeElement.querySelector(
      'input[type="checkbox"]',
    ) as HTMLInputElement;
    const acceptButton = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;

    expect(fixture.nativeElement.textContent).toContain('Versión');
    expect(fixture.nativeElement.textContent).toContain('1.0.0');
    expect(fixture.nativeElement.textContent).toContain('a'.repeat(64));
    expect(fixture.nativeElement.textContent).toContain(
      'Contenido aprobado de prueba.',
    );
    expect(checkbox.checked).toBe(false);
    expect(acceptButton.disabled).toBe(true);
    http.expectNone('/api/v1/admin/terms/acceptances');
  });

  it('envía versión y SHA-256 solo después de marcar y enviar expresamente', () => {
    const fixture = TestBed.createComponent(AdminTermsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/terms/current').flush(publishedDocument());
    fixture.detectChanges();

    const checkbox = fixture.nativeElement.querySelector(
      'input[type="checkbox"]',
    ) as HTMLInputElement;
    const acceptButton = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(acceptButton.disabled).toBe(true);

    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    expect(acceptButton.disabled).toBe(false);
    http.expectNone('/api/v1/admin/terms/acceptances');

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();

    const request = http.expectOne('/api/v1/admin/terms/acceptances');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      version: '1.0.0',
      contentSha256: 'a'.repeat(64),
      accepted: true,
    });
    request.flush({
      acceptanceId: 'acceptance-1',
      adminUserId: 'admin-1',
      organizationId: 'organization-1',
      documentVersion: '1.0.0',
      contentSha256: 'a'.repeat(64),
      synthetic: false,
      acceptedAt: '2026-10-14T12:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Esta versión ya fue aceptada',
    );
    expect(fixture.nativeElement.textContent).toContain(
      '2026-10-14T12:00:00Z',
    );
  });

  it('presenta el 404 como ausencia de versión aprobada publicada', () => {
    const fixture = TestBed.createComponent(AdminTermsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/terms/current').flush(
      {
        status: 404,
        code: 'RESOURCE_NOT_FOUND',
        message: 'No hay una versión publicada.',
      },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'No hay una versión aprobada publicada',
    );
    expect(
      fixture.nativeElement.querySelector('input[type="checkbox"]'),
    ).toBeNull();
    http.expectNone('/api/v1/admin/terms/acceptances');
  });

  function publishedDocument() {
    return {
      documentKey: 'ADMIN_TERMS',
      version: '1.0.0',
      content: 'Contenido aprobado de prueba.',
      contentSha256: 'a'.repeat(64),
      synthetic: false,
      accepted: false,
      acceptedAt: null,
    };
  }
});
