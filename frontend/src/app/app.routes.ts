import { Routes } from '@angular/router';

import { authGuard, anonymousGuard } from './core/auth/auth.guard';
import { adminTermsGuard } from './core/auth/admin-terms.guard';
import { CRM_ROLES } from './core/auth/auth.models';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: 'admin-activation',
    title: 'Activar ADMIN | Ruta Fija',
    loadComponent: () => import('./features/admin-activation/admin-activation.page').then((module) => module.AdminActivationPage),
  },
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
    canActivateChild: [adminTermsGuard],
    loadComponent: () =>
      import('./layout/app-shell.component').then(
        (module) => module.AppShellComponent,
      ),
    children: [
      {path:'platform-audit',title:'Auditoría de plataforma | Ruta Fija',canActivate:[roleGuard],data:{roles:['SUPER_ADMIN']},loadComponent:()=>import('./features/audit/audit.page').then(m=>m.AuditPage)},
      {path:'notices',title:'Bandeja de avisos | Ruta Fija',canActivate:[roleGuard],data:{roles:['ADMIN']},loadComponent:()=>import('./features/announcements/notices.page').then(m=>m.NoticesPage)},
      {
        path: 'platform-admins',
        title: 'Cuentas ADMIN | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['SUPER_ADMIN'] },
        loadComponent: () => import('./features/platform-admins/platform-admins.page').then((module) => module.PlatformAdminsPage),
      },
      {
        path: 'admin-terms',
        title: 'Términos ADMIN | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/terms/admin-terms.page').then(
            (module) => module.AdminTermsPage,
          ),
      },
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
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/users/users.page').then((module) => module.UsersPage),
      },
      {
        path: 'invitations',
        title: 'Invitaciones | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/invitations/invitations.page').then(
            (module) => module.InvitationsPage,
          ),
      },
      {
        path: 'onboarding-requests',
        title: 'Solicitudes de incorporación | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/onboarding/onboarding-requests.page').then(
            (module) => module.OnboardingRequestsPage,
          ),
      },
      {
        path: 'organization',
        title: 'Organización | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/organization/organization.page').then(
            (module) => module.OrganizationPage,
          ),
      },
      {
        path: 'groups',
        title: 'Grupos | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/groups/groups.page').then((module) => module.GroupsPage),
      },
      {
        path: 'drivers/:driverId/qualification',
        title: 'Habilitación del conductor | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/qualification/driver-qualification.page').then(
          (module) => module.DriverQualificationPage,
        ),
      },
      {
        path: 'drivers',
        title: 'Conductores | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/drivers/drivers.page').then((module) => module.DriversPage),
      },
      {
        path: 'vehicles/:vehicleId/maintenance',
        title: 'Mantenimiento | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/maintenance/maintenance.page').then((module) => module.MaintenancePage),
      },
      {
        path: 'vehicles',
        title: 'Vehículos | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/vehicles/vehicles.page').then((module) => module.VehiclesPage),
      },
      {
        path: 'assignments',
        title: 'Asignaciones | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/assignments/assignments.page').then(
            (module) => module.AssignmentsPage,
          ),
      },
      {
        path:'service-types',title:'Tipos de servicio | Ruta Fija',canActivate:[roleGuard],data:{roles:['ADMIN']},
        loadComponent:()=>import('./features/assignments/service-types.page').then(m=>m.ServiceTypesPage),
      },
      {
        path: 'routes',
        title: 'Rutas | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/routes/routes.page').then((module) => module.RoutesPage),
      },
      {
        path: 'incidents',
        title: 'Incidencias | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/incidents/incidents.page').then(
            (module) => module.IncidentsPage,
          ),
      },
      {
        path: 'announcements',
        title: 'Comunicados | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/announcements/announcements.page').then(
            (module) => module.AnnouncementsPage,
          ),
      },
      {
        path: 'reports',
        title: 'Reportes | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/reports/reports.page').then(
            (module) => module.ReportsPage,
          ),
      },
      {
        path: 'audit',
        title: 'Auditoría | Ruta Fija',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
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
