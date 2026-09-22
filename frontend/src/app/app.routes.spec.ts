import { Route } from '@angular/router';
import { describe, expect, it } from 'vitest';

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
  it('reserva Organización, Reportes y Auditoría al rol ADMIN', () => {
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
  });
});
