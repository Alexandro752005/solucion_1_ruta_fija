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
    fullName: 'Admin Demo',
    email: 'admin@example.test',
    role: 'ADMIN',
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
      fullName: 'Admin Demo',
      email: 'admin@example.test',
      role: 'ADMIN',
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

  it('muestra la topbar completa de ADMIN sin sidebar', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();

    const content = fixture.nativeElement.textContent as string;
    expect(fixture.nativeElement.querySelector('.primary-navigation')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.sidebar')).toBeNull();
    expect(content).toContain('Usuarios');
    expect(content).toContain('Invitaciones');
    expect(content).toContain('Organización');
    expect(content).toContain('Reportes');
    expect(content).toContain('Auditoría');
    expect(content).toContain('Admin');
    expect(content).toContain('CUENTA-admin-1');
    expect(content).not.toContain('Admin Demo');
    expect(content).not.toContain('admin@example.test');
    expect(content).not.toContain('Organizaciones');
    const links = Array.from(fixture.nativeElement.querySelectorAll('.primary-navigation a'))
      .map((element) => (element as HTMLElement).textContent?.trim());
    expect(links).toEqual([
      'Resumen',
      'Usuarios',
      'Invitaciones',
      'Solicitudes',
      'Organización',
      'Grupos',
      'Conductores',
      'Vehículos',
      'Asignaciones',
      'Rutas',
      'Incidencias',
      'Comunicados',
      'Bandeja',
      'Reportes',
      'Auditoría',
    ]);
  });

  it('limita SUPER_ADMIN a resumen y administración/auditoría de plataforma', () => {
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
    expect(links).toEqual(['Resumen', 'Organizaciones', 'Cuentas ADMIN', 'Auditoría de plataforma']);
    expect(links).not.toContain('Conductores');
    expect(links).not.toContain('Reportes');
  });
});
