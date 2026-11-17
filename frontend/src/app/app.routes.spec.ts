import { Route } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { adminTermsGuard } from './core/auth/admin-terms.guard';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { routes } from './app.routes';

function childRoute(path: string): Route {
  const shell = routes.find((route) => route.path === '' && route.children !== undefined);
  const route = shell?.children?.find((candidate) => candidate.path === path);
  if (route === undefined) {
    throw new Error(`No se encontró la ruta ${path}`);
  }
  return route;
}

describe('rutas del CRM por rol', () => {
  it('restringe el nuevo mapa a ADMIN y revalida sesión', () => {
    expect(childRoute('live-map').data?.['roles']).toEqual(['ADMIN']);
    expect(childRoute('live-map').canActivate).toEqual([authGuard, roleGuard]);
  });
  it('revalida sesión, rol y términos al cambiar la intención de Asignaciones', () => {
    const route = childRoute('assignments');
    expect(route.runGuardsAndResolvers).toBe('paramsOrQueryParamsChange');
    expect(route.canActivate).toEqual([authGuard, roleGuard]);
  });
  it('protege las solicitudes y el expediente con el guard de rol ADMIN', () => {
    const route = childRoute('onboarding-requests');
    expect(route.data?.['roles']).toEqual(['ADMIN']);
    expect(route.canActivate).toContain(roleGuard);
  });
  it('expone términos ADMIN y protege el acceso a las demás rutas del CRM', () => {
    const shell = routes.find((route) => route.path === '' && route.children !== undefined);

    expect(childRoute('admin-terms').data?.['roles']).toEqual(['ADMIN']);
    expect(shell?.canActivateChild).toContain(adminTermsGuard);
  });

  it('reserva Organización, Reportes y Auditoría al rol ADMIN', () => {
    expect(childRoute('invitations').data?.['roles']).toEqual(['ADMIN']);
    expect(childRoute('organization').data?.['roles']).toEqual(['ADMIN']);
    expect(childRoute('reports').data?.['roles']).toEqual(['ADMIN']);
    expect(childRoute('audit').data?.['roles']).toEqual(['ADMIN']);
  });

  it('mantiene toda la operación del tenant para ADMIN', () => {
    for (const path of ['groups', 'drivers', 'vehicles', 'assignments', 'incidents', 'announcements']) {
      expect(childRoute(path).data?.['roles']).toEqual(['ADMIN']);
    }
  });

  it('reserva Organizaciones al superadministrador', () => {
    expect(childRoute('organizations').data?.['roles']).toEqual(['SUPER_ADMIN']);
    expect(childRoute('platform-admins').data?.['roles']).toEqual(['SUPER_ADMIN']);
    expect(childRoute('platform-admins').canActivate).toContain(roleGuard);
  });
  it('permite reclamar activación sin sesión operativa ni guards del CRM', () => {
    const activation = routes.find((route) => route.path === 'admin-activation');
    expect(activation?.loadComponent).toBeDefined();
    expect(activation?.canActivate).toBeUndefined();
  });
});
