import { Routes } from '@angular/router';

import { authGuard, anonymousGuard } from './core/auth/auth.guard';
import { CRM_ROLES } from './core/auth/auth.models';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión | Ruta Fija',
    canActivate: [anonymousGuard],
    loadComponent: () =>
      import('./features/auth/login.page').then((module) => module.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layout/app-shell.component').then(
        (module) => module.AppShellComponent,
      ),
    children: [
      {
        path: 'dashboard',
        title: 'Inicio | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: CRM_ROLES },
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then(
            (module) => module.DashboardPage,
          ),
      },
      {
        path: 'users',
        title: 'Usuarios | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () =>
          import('./features/users/users.page').then((module) => module.UsersPage),
      },
      {
        path: 'organization',
        title: 'Organización | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () =>
          import('./features/organization/organization.page').then(
            (module) => module.OrganizationPage,
          ),
      },
      {
        path: 'groups',
        title: 'Grupos | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR', 'COORDINADOR'] },
        loadComponent: () =>
          import('./features/groups/groups.page').then((module) => module.GroupsPage),
      },
      {
        path: 'drivers',
        title: 'Conductores | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR', 'COORDINADOR'] },
        loadComponent: () =>
          import('./features/drivers/drivers.page').then((module) => module.DriversPage),
      },
      {
        path: 'vehicles',
        title: 'Vehículos | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR', 'COORDINADOR'] },
        loadComponent: () =>
          import('./features/vehicles/vehicles.page').then((module) => module.VehiclesPage),
      },
      {
        path: 'assignments',
        title: 'Asignaciones | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR', 'COORDINADOR'] },
        loadComponent: () =>
          import('./features/assignments/assignments.page').then(
            (module) => module.AssignmentsPage,
          ),
      },
      {
        path: 'incidents',
        title: 'Incidencias | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR', 'COORDINADOR'] },
        loadComponent: () =>
          import('./features/incidents/incidents.page').then(
            (module) => module.IncidentsPage,
          ),
      },
      {
        path: 'announcements',
        title: 'Comunicados | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR', 'COORDINADOR'] },
        loadComponent: () =>
          import('./features/announcements/announcements.page').then(
            (module) => module.AnnouncementsPage,
          ),
      },
      {
        path: 'reports',
        title: 'Reportes | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () =>
          import('./features/reports/reports.page').then(
            (module) => module.ReportsPage,
          ),
      },
      {
        path: 'audit',
        title: 'Auditoría | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMINISTRADOR'] },
        loadComponent: () =>
          import('./features/audit/audit.page').then(
            (module) => module.AuditPage,
          ),
      },
      {
        path: 'organizations',
        title: 'Organizaciones | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN'] },
        loadComponent: () =>
          import('./features/organizations/organizations.page').then(
            (module) => module.OrganizationsPage,
          ),
      },
      {
        path: 'forbidden',
        title: 'Acceso restringido | Ruta Fija',
        loadComponent: () =>
          import('./features/errors/forbidden.page').then(
            (module) => module.ForbiddenPage,
          ),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  {
    path: '**',
    title: 'Página no encontrada | Ruta Fija',
    loadComponent: () =>
      import('./features/errors/not-found.page').then(
        (module) => module.NotFoundPage,
      ),
  },
];
