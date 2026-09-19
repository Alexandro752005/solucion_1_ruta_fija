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
  it('reserva Organización, Reportes y Auditoría al administrador', () => {
    expect(childRoute('organization').data?.['roles']).toEqual(['ADMINISTRADOR']);
    expect(childRoute('reports').data?.['roles']).toEqual(['ADMINISTRADOR']);
    expect(childRoute('audit').data?.['roles']).toEqual(['ADMINISTRADOR']);
  });

  it('mantiene operación para administrador y coordinador', () => {
    for (const path of ['groups', 'drivers', 'vehicles', 'assignments', 'incidents', 'announcements']) {
      expect(childRoute(path).data?.['roles']).toEqual(['ADMINISTRADOR', 'COORDINADOR']);
    }
  });

  it('reserva Organizaciones al superadministrador', () => {
    expect(childRoute('organizations').data?.['roles']).toEqual(['SUPER_ADMIN']);
  });
});
