import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { AuthSessionService } from '../core/auth/auth-session.service';
import { SessionUser } from '../core/auth/auth.models';
import { RuntimeConfigService } from '../core/config/runtime-config.service';
import { AppShellComponent } from './app-shell.component';

describe('AppShellComponent', () => {
  const user = signal<SessionUser | null>({
    id: 'admin-1',
    fullName: 'Administradora Demo',
    email: 'admin@example.test',
    role: 'ADMINISTRADOR',
    organizationId: 'organization-1',
    organizationName: 'Transportes Demo',
  });
  const session = {
    user: user.asReadonly(),
    logout: () => of(undefined),
    hasAnyRole: (roles: readonly string[]) => {
      const role = user()?.role;
      return role !== undefined && roles.includes(role);
    },
  };

  beforeEach(async () => {
    user.set({
      id: 'admin-1',
      fullName: 'Administradora Demo',
      email: 'admin@example.test',
      role: 'ADMINISTRADOR',
      organizationId: 'organization-1',
      organizationName: 'Transportes Demo',
    });
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: session },
        { provide: RuntimeConfigService, useValue: {} },
      ],
    }).compileComponents();
  });

  it('muestra la topbar completa del administrador sin sidebar', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    const content = fixture.nativeElement.textContent as string;
    expect(fixture.nativeElement.querySelector('.primary-navigation')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.sidebar')).toBeNull();
    expect(content).toContain('Usuarios');
    expect(content).toContain('Organización');
    expect(content).toContain('Reportes');
    expect(content).toContain('Auditoría');
    expect(content).not.toContain('Organizaciones');
  });

  it('limita la navegación global a Resumen y Organizaciones', () => {
    user.set({
      id: 'super-1',
      fullName: 'Superadministrador Demo',
      email: 'super@example.test',
      role: 'SUPER_ADMIN',
    });
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    const links = Array.from(fixture.nativeElement.querySelectorAll('.primary-navigation a'))
      .map((element) => (element as HTMLElement).textContent?.trim());
    expect(links).toEqual(['Resumen', 'Organizaciones']);
  });
});
