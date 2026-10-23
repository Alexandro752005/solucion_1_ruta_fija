import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { LoginPage } from './login.page';

describe('LoginPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  it('muestra validaciones y no envía un formulario vacío', () => {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.componentInstance.form.invalid).toBe(true);
    expect(fixture.nativeElement.textContent).toContain(
      'Ingresa tu correo electrónico.',
    );
    http.expectNone('/api/v1/auth/login');
  });

  it.each([
    { email: '', firstInvalid: '#email' },
    { email: 'correo-invalido', firstInvalid: '#email' },
    { email: 'sintetico@rutafija.test', firstInvalid: '#password' },
  ])('enfoca $firstInvalid antes del próximo render', ({ email, firstInvalid }) => {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.email.setValue(email);
    const input: HTMLInputElement = fixture.nativeElement.querySelector(firstInvalid);
    const focus = vi.spyOn(input, 'focus');
    // The DOM has not rendered the new validation state yet. Model validity
    // must determine focus rather than an aria-invalid attribute updated later.
    expect(input.getAttribute('aria-invalid')).toBe('false');

    fixture.componentInstance.submit();

    expect(focus).toHaveBeenCalledExactlyOnceWith();
    http.expectNone('/api/v1/auth/login');
    fixture.destroy();
  });
});
