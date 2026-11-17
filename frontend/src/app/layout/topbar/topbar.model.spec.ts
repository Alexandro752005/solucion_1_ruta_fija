import { describe, expect, it } from 'vitest';
import { routes } from '../../app.routes';
import { ADMIN_TOPBAR_MODEL as model, selectTopbarModel, SUPER_ADMIN_TOPBAR_MODEL, TopbarItem } from './topbar.model';

describe('contrato de navegación ADMIN', () => {
  it('conserva los quince destinos, términos y activa únicamente el mapa ADMIN', () => {
    const destinations = [model.dashboard, ...model.groups.flatMap(g => g.items), model.inbox, ...model.accountLinks];
    const existing = destinations.filter((i): i is TopbarItem => i !== null && i.availability === 'available');
    expect(existing.map(i => i.route).sort()).toEqual([
      '/dashboard', '/users', '/invitations', '/onboarding-requests', '/organization', '/groups', '/drivers', '/vehicles', '/assignments', '/routes', '/incidents', '/announcements', '/notices', '/reports', '/audit', '/admin-terms', '/live-map',
    ].sort());
    const children = routes.find(r => r.path === '' && r.children)?.children ?? [];
    for (const item of existing) expect(children.find(r => `/${r.path}` === item.route)?.data?.['roles']).toContain('ADMIN');
    expect(model.groups.map(g => g.label)).toEqual(['Gestión de personas', 'Gestión de flota', 'Operación', 'Monitoreo', 'Control', 'Mensajes', 'Eventos']);
    expect(model.groups.flatMap(g => g.items).filter(i => i.availability === 'planned')).toEqual([]);
    expect(model.quickActions[1].availability).toBe('available');
    expect(model.quickActions[1].route).toBe('/live-map');
    expect(model.quickActions[0].queryParams).toEqual({ action: 'new' });
  });
});

describe('contrato de navegación por rol CRM', () => {
  it('selecciona proyecciones constantes y no concede una proyección ADMIN por defecto', () => {
    expect(selectTopbarModel('ADMIN')).toBe(model);
    expect(selectTopbarModel('SUPER_ADMIN')).toBe(SUPER_ADMIN_TOPBAR_MODEL);
    for (const role of [null, undefined, '', 'CONDUCTOR', 'UNKNOWN', 'admin', ' ADMIN ']) {
      expect(selectTopbarModel(role)).toBeNull();
    }
  });

  it('limita SUPER_ADMIN a sus cuatro destinos existentes y dos categorías', () => {
    const platform = SUPER_ADMIN_TOPBAR_MODEL;
    const destinations = [platform.dashboard, ...platform.groups.flatMap(group => group.items)];
    expect(destinations.map(item => item.route)).toEqual(['/dashboard', '/organizations', '/platform-admins', '/platform-audit']);
    expect(new Set(destinations.map(item => item.route)).size).toBe(4);
    expect(platform.roles).toEqual(['SUPER_ADMIN']);
    expect(platform.groups.map(group => group.label)).toEqual(['Administración de plataforma', 'Control de plataforma']);
    expect(platform.inbox).toBeNull();
    expect(platform.quickActions).toEqual([]);
    expect(platform.accountLinks).toEqual([]);
    const children = routes.find(route => route.path === '' && route.children)?.children ?? [];
    for (const item of destinations) {
      expect(children.find(route => `/${route.path}` === item.route)?.data?.['roles']).toContain('SUPER_ADMIN');
      expect(item.availability).toBe('available');
    }
  });

  it('conserva la asociación de Tipos de servicio en el catálogo ADMIN', () => {
    expect(model.groups.find(group => group.id === 'operations')?.associatedRoutes).toEqual(['/service-types']);
    expect(SUPER_ADMIN_TOPBAR_MODEL.groups.flatMap(group => group.associatedRoutes ?? [])).toEqual([]);
  });
});
