import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { AuthSessionService } from '../core/auth/auth-session.service';
import { SessionUser, UserRole } from '../core/auth/auth.models';
import { RuntimeConfigService } from '../core/config/runtime-config.service';
import { AppShellComponent } from './app-shell.component';

import { TOPBAR_ACTIVATION } from './topbar-activation';
import { SHELL_CONTEXT_RELOAD } from './shell-session-context';
import { vi } from 'vitest';
const policyRoles: UserRole[] = [];

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
    status: () => 'authenticated',
    logout: () => of(undefined),
    hasAnyRole: (roles: readonly string[]) => {
      const role = user()?.role;
      return role !== undefined && roles.includes(role);
    },
  };

  beforeEach(async () => {
    policyRoles.splice(0, policyRoles.length, 'ADMIN');
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
        { provide: TOPBAR_ACTIVATION, useValue: policyRoles },
        { provide: SHELL_CONTEXT_RELOAD, useValue: vi.fn() },
      ],
    }).compileComponents();
  });

  it('integra ADMIN, conserva privacidad y mantiene el outlet al cambiar sólo contexto visual', () => {
    const fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('rf-crm-topbar')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.primary-navigation')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Dashboard');
    expect(fixture.nativeElement.textContent).not.toContain('Admin Demo');
    expect(fixture.nativeElement.textContent).not.toContain('admin@example.test');
    fixture.nativeElement.querySelector('#topbar-account-trigger').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('CUENTA-admin-1');
    expect(fixture.nativeElement.textContent).toContain('Transportes Demo');
    const outlet = fixture.nativeElement.querySelector('router-outlet');
    user.set({ ...user()!, fullName: 'Privado actualizado', organizationName: 'Nuevo nombre visible' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('rf-crm-topbar')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('router-outlet')).toBe(outlet);
    expect(fixture.nativeElement.textContent).not.toContain('Transportes Demo');
  });

  it('conserva la navegación anterior ADMIN como reversión de la política', () => {
    policyRoles.length = 0;
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

  it('proyecta la misma barra para SUPER_ADMIN habilitado sin heredar contexto o controles ADMIN', () => {
    policyRoles.push('SUPER_ADMIN');
    user.set({ id: 'super-1', fullName: 'Persona privada', email: 'secret@example.test', role: 'SUPER_ADMIN', organizationName: 'No usar este contexto tenant' });
    const fixture = TestBed.createComponent(AppShellComponent); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('rf-crm-topbar')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-inbox]')).toBeNull();
    expect(fixture.nativeElement.querySelector('#topbar-actions-trigger')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Superadministrador Ruta Fija');
    fixture.nativeElement.querySelector('#topbar-account-trigger').click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ámbito de plataforma');
    expect(fixture.nativeElement.textContent).not.toContain('No usar este contexto tenant');
    expect(fixture.nativeElement.textContent).not.toContain('Persona privada');
    expect(fixture.nativeElement.textContent).not.toContain('secret@example.test');
    expect(fixture.nativeElement.querySelector('[data-item=organization]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-item=admin-terms]')).toBeNull();
  });
});
