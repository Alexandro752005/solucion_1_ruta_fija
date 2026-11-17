import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CrmTopbarComponent } from './crm-topbar.component';
import { ADMIN_TOPBAR_MODEL, SUPER_ADMIN_TOPBAR_MODEL, TopbarModel } from './topbar.model';

@Component({ selector: 'rf-topbar-destination-fixture', template: '<p>Destino sintético</p>' })
class DestinationFixture {}

describe('CrmTopbarComponent', () => {
  beforeEach(async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: 1440 });
    await TestBed.configureTestingModule({ imports: [CrmTopbarComponent], providers: [provideRouter([
      { path: 'vehicles', component: DestinationFixture },
      { path: 'service-types', component: DestinationFixture },
      { path: 'organizations', component: DestinationFixture },
    ])] }).compileComponents();
  });

  function setup(model: TopbarModel = ADMIN_TOPBAR_MODEL) {
    const fixture = TestBed.createComponent(CrmTopbarComponent);
    fixture.componentRef.setInput('model', model);
    fixture.componentRef.setInput('contextLabel', model === SUPER_ADMIN_TOPBAR_MODEL ? 'Ámbito de plataforma' : 'Transportes de prueba');
    fixture.componentRef.setInput('sessionContextKey', 'synthetic-admin-context');
    fixture.componentRef.setInput('accountReference', 'CUENTA-sintetica');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const button = (id: string) => root.querySelector<HTMLButtonElement>(`#${id}`)!;
    return { fixture, root, button };
  }

  it('omits the actions trigger when the supplied role has no quick actions', () => {
    const fixture = TestBed.createComponent(CrmTopbarComponent);
    fixture.componentRef.setInput('model', { ...ADMIN_TOPBAR_MODEL, quickActions: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#topbar-actions-trigger')).toBeNull();
  });

  it('uses identity supplied by the role model instead of a fixed ADMIN identity', () => {
    const fixture = TestBed.createComponent(CrmTopbarComponent);
    fixture.componentRef.setInput('model', { ...ADMIN_TOPBAR_MODEL, identity: {
      avatarText: 'SA', displayName: 'Superadministrador Ruta Fija', roleLabel: 'Superadministrador',
      headerLabel: 'Cabecera de plataforma', modulesLabel: 'Módulos de plataforma',
      accountLabel: 'Cuenta de superadministrador', accountPanelLabel: 'Cuenta de plataforma',
    } });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.avatar')?.textContent).toBe('SA');
    expect(fixture.nativeElement.querySelector('.account-copy strong')?.textContent).toBe('Superadministrador Ruta Fija');
  });

  it('starts with closed disclosures and exposes the inbox without a fabricated count', () => {
    const { root, button } = setup();
    expect(button('topbar-modules-trigger').getAttribute('aria-expanded')).toBe('false');
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(root.querySelector('[data-inbox]')?.getAttribute('href')).toBe('/notices');
    expect(root.querySelector('[data-unread-count]')).toBeNull();
    expect(root.textContent).toContain('Administrador Ruta Fija');
    expect(root.querySelector('[role="menubar"]')).toBeNull();
  });

  it('requires an explicit role model instead of falling back to ADMIN', () => {
    const fixture = TestBed.createComponent(CrmTopbarComponent);
    try {
      expect(() => fixture.detectChanges()).toThrow();
    } finally {
      fixture.destroy();
    }
  });

  it('renders SUPER_ADMIN with only its identity and no tenant controls or residual divider', () => {
    const { root, button } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    expect(root.querySelector('.avatar')?.textContent).toBe('SA');
    expect(root.querySelector('.account-copy strong')?.textContent).toBe('Superadministrador Ruta Fija');
    expect(root.querySelector('.account-copy small')?.textContent).toBe('Superadministrador');
    expect(root.querySelector('header')?.getAttribute('aria-label')).toBe('Cabecera de plataforma');
    expect(button('topbar-account-trigger').getAttribute('aria-label')).toBe('Cuenta de superadministrador');
    expect(root.querySelector('[data-inbox]')).toBeNull();
    expect(root.querySelector('.account-divider')).toBeNull();
    expect(root.querySelector('#topbar-actions-trigger')).toBeNull();
    expect(root.querySelector('[data-item="live-monitoring"]')).toBeNull();
    expect(root.textContent).not.toContain('Administrador Ruta Fija');
  });

  it('offers exactly the two platform categories and three existing grouped destinations', () => {
    const { fixture, root, button } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    expect(root.querySelector('[data-panel="modules"]')?.getAttribute('aria-label')).toBe('Módulos de plataforma');
    const groups = Array.from(root.querySelectorAll<HTMLButtonElement>('[data-group-trigger]'));
    expect(groups.map(group => group.dataset['groupLabel'])).toEqual(['Administración de plataforma', 'Control de plataforma']);
    const paths: string[] = [];
    for (const group of groups) {
      group.click(); fixture.detectChanges();
      paths.push(...Array.from(root.querySelectorAll<HTMLAnchorElement>('[data-subpanel] a')).map(link => link.getAttribute('href')!));
    }
    expect(paths).toEqual(['/organizations', '/platform-admins', '/platform-audit']);
    expect(root.querySelector('[data-item="organization"]')).toBeNull();
    expect(root.querySelector('[data-item="admin-terms"]')).toBeNull();
    expect(root.querySelector('[data-item="live-map"]')).toBeNull();
  });

  it('keeps only platform context and existing logout in the SUPER_ADMIN account surface', () => {
    const { fixture, root, button } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    button('topbar-account-trigger').click(); fixture.detectChanges();
    const account = root.querySelector<HTMLElement>('#topbar-account-panel')!;
    expect(account.getAttribute('aria-label')).toBe('Cuenta de plataforma');
    expect(account.textContent).toContain('Ámbito de plataforma');
    expect(account.textContent).toContain('CUENTA-sintetica');
    expect(account.querySelectorAll('a')).toHaveLength(0);
    expect(account.querySelectorAll('button')).toHaveLength(1);
    expect(account.querySelector('[data-logout]')?.textContent).toBe('Cerrar sesión');
    expect(account.querySelector('[data-logout]')?.classList.contains('logout-control--standalone')).toBe(true);
  });

  it('omits the quick actions section and links in the compact SUPER_ADMIN surface', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    const { fixture, root, button } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    button('topbar-compact-trigger').click(); fixture.detectChanges();
    const compact = root.querySelector<HTMLElement>('[data-panel="compact"]')!;
    expect(compact.textContent).toContain('Dashboard');
    expect(compact.textContent).not.toContain('Acciones rápidas');
    expect(compact.querySelectorAll('[data-group-trigger]')).toHaveLength(2);
    expect(compact.querySelector('[data-item="new-assignment"]')).toBeNull();
    expect(compact.querySelector('[data-item="live-monitoring"]')).toBeNull();
    button('topbar-group-platform-administration').click(); fixture.detectChanges();
    expect(Array.from(compact.querySelectorAll<HTMLAnchorElement>('[data-subpanel] a')).map(link => link.getAttribute('href')))
      .toEqual(['/organizations', '/platform-admins']);
  });

  it('cannot open an empty actions surface through a stale or programmatic trigger', () => {
    const { fixture, root } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    fixture.componentInstance.togglePanel('actions', new Event('click'));
    fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(fixture.componentInstance.panel()).toBeNull();
  });

  it('closes the previous role surface when the supplied role projection changes', () => {
    const { fixture, root, button } = setup();
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    button('topbar-group-people').click(); fixture.detectChanges();
    fixture.componentRef.setInput('model', SUPER_ADMIN_TOPBAR_MODEL);
    fixture.componentRef.setInput('contextLabel', 'Ámbito de plataforma');
    fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(fixture.componentInstance.expandedGroup()).toBeNull();
    expect(root.querySelector('.avatar')?.textContent).toBe('SA');
    expect(root.querySelector('[data-inbox]')).toBeNull();
  });

  it('closes surfaces on a stable context-key change but preserves them for a cosmetic context label change', () => {
    const { fixture, root, button } = setup();
    button('topbar-account-trigger').click(); fixture.detectChanges();
    fixture.componentRef.setInput('contextLabel', 'Transportes con nombre actualizado'); fixture.detectChanges();
    expect(root.querySelector('[data-panel="account"]')?.textContent).toContain('Transportes con nombre actualizado');
    fixture.componentRef.setInput('sessionContextKey', 'another-organization-context'); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
  });

  it('shows logout pending as disabled and prevents another logout emission', () => {
    const { fixture, root, button } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    const logout = vi.fn(); fixture.componentInstance.logoutRequested.subscribe(logout);
    button('topbar-account-trigger').click(); fixture.detectChanges();
    fixture.componentRef.setInput('loggingOut', true); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    button('topbar-account-trigger').click(); fixture.detectChanges();
    const control = root.querySelector<HTMLButtonElement>('[data-logout]')!;
    expect(control.disabled).toBe(true);
    expect(control.getAttribute('aria-busy')).toBe('true');
    expect(control.textContent).toBe('Cerrando…');
    control.click(); fixture.componentInstance.logout();
    expect(logout).not.toHaveBeenCalled();
  });

  it('preserves seven ordered categories and every operational destination', () => {
    const { fixture, root, button } = setup();
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    const groups = Array.from(root.querySelectorAll<HTMLButtonElement>('[data-group-trigger]'));
    expect(groups.map(group => group.dataset['groupLabel'])).toEqual(['Gestión de personas', 'Gestión de flota', 'Operación', 'Monitoreo', 'Control', 'Mensajes', 'Eventos']);
    const paths: string[] = [];
    for (const group of groups) {
      group.click(); fixture.detectChanges();
      paths.push(...Array.from(root.querySelectorAll<HTMLAnchorElement>('[data-subpanel] a')).map(link => link.getAttribute('href')!));
    }
    expect(paths).toEqual(['/users', '/invitations', '/onboarding-requests', '/groups', '/drivers', '/vehicles', '/assignments', '/routes', '/live-map', '/audit', '/reports', '/announcements', '/incidents']);
  });

  it('activates both existing ADMIN map accesses and preserves the assignment form shortcut', () => {
    const { fixture, root, button } = setup();
    button('topbar-actions-trigger').click(); fixture.detectChanges();
    expect(root.querySelector('[data-item="new-assignment"]')?.getAttribute('href')).toBe('/assignments?action=new');
    const monitoring = root.querySelector<HTMLAnchorElement>('[data-item="live-monitoring"]')!;
    expect(monitoring.getAttribute('href')).toBe('/live-map');
    expect(monitoring.textContent).not.toContain('Próximamente');
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    expect(root.querySelector('[data-panel="actions"]')).toBeNull();
    button('topbar-group-monitoring').click(); fixture.detectChanges();
    expect(root.querySelector<HTMLAnchorElement>('[data-item="live-map"]')?.getAttribute('href')).toBe('/live-map');
  });

  it('closes the inner disclosure first and returns focus on Escape', async () => {
    const { fixture, root, button } = setup();
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    button('topbar-group-people').click(); fixture.detectChanges();
    root.querySelector<HTMLElement>('[data-subpanel] a')!.focus();
    root.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('[data-subpanel]')).toBeNull();
    expect(document.activeElement?.id).toBe('topbar-group-people');
    root.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(document.activeElement?.id).toBe('topbar-modules-trigger');
  });

  it('keeps internal focus, closes on external interaction and preserves external focus', () => {
    const { fixture, root, button } = setup();
    button('topbar-account-trigger').click(); fixture.detectChanges();
    expect(root.querySelector('[data-panel="account"]')?.textContent).toContain('CUENTA-sintetica');
    const outside = document.createElement('button'); document.body.append(outside); outside.focus(); outside.click(); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(document.activeElement).toBe(outside); outside.remove();
  });

  it('supports arrow navigation into and out of a category without requiring hover', async () => {
    const { fixture, root, button } = setup();
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    button('topbar-group-people').focus();
    button('topbar-group-people').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges(); await fixture.whenStable();
    expect(document.activeElement?.getAttribute('data-item')).toBe('users');
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true })); fixture.detectChanges();
    expect(root.querySelector('[data-subpanel]')).toBeNull();
    expect(document.activeElement?.id).toBe('topbar-group-people');
  });

  it('closes on successful navigation and preserves a persistent group indicator', async () => {
    const { fixture, root, button } = setup();
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/vehicles'); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(button('topbar-modules-trigger').classList.contains('contains-current')).toBe(true);
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    expect(button('topbar-group-fleet').classList.contains('contains-current')).toBe(true);
  });

  it('uses model route associations to retain the ADMIN operation indicator on an internal route', async () => {
    const { fixture, button } = setup();
    await TestBed.inject(Router).navigateByUrl('/service-types'); fixture.detectChanges();
    expect(fixture.componentInstance.activeGroupId()).toBe('operations');
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    expect(button('topbar-group-operations').classList.contains('contains-current')).toBe(true);
  });

  it('uses the shared keyboard behavior for a platform category and returns to its own trigger', async () => {
    const { fixture, root, button } = setup(SUPER_ADMIN_TOPBAR_MODEL);
    button('topbar-modules-trigger').click(); fixture.detectChanges();
    button('topbar-group-platform-administration').focus();
    button('topbar-group-platform-administration').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges(); await fixture.whenStable();
    expect(document.activeElement?.getAttribute('data-item')).toBe('organizations');
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); fixture.detectChanges();
    expect(root.querySelector('[data-subpanel]')).toBeNull();
    expect(document.activeElement?.id).toBe('topbar-group-platform-administration');
    document.activeElement!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); fixture.detectChanges();
    expect(document.activeElement?.id).toBe('topbar-modules-trigger');
  });

  it('emits logout without owning authentication or making requests', () => {
    const { fixture, button, root } = setup();
    const logout = vi.fn(); fixture.componentInstance.logoutRequested.subscribe(logout);
    button('topbar-account-trigger').click(); fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-logout]')!.click(); fixture.detectChanges();
    expect(logout).toHaveBeenCalledTimes(1);
    expect(root.querySelector('[data-panel]')).toBeNull();
  });

  it('closes account context when the authenticated account changes', () => {
    const { fixture, button, root } = setup();
    button('topbar-account-trigger').click(); fixture.detectChanges();
    expect(root.querySelector('[data-panel="account"]')).not.toBeNull();
    fixture.componentRef.setInput('accountReference', 'CUENTA-otra-sintetica'); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(root.textContent).not.toContain('CUENTA-sintetica');
  });

  it('provides compact navigation and closes it when the viewport changes without stealing main focus', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    const { fixture, root, button } = setup();
    button('topbar-compact-trigger').click(); fixture.detectChanges();
    expect(root.querySelector('[data-panel="compact"]')).not.toBeNull();
    const main = document.createElement('button'); document.body.append(main); main.focus();
    window.dispatchEvent(new Event('resize')); fixture.detectChanges();
    expect(root.querySelector('[data-panel]')).toBeNull();
    expect(document.activeElement).toBe(main); main.remove();
  });
});
