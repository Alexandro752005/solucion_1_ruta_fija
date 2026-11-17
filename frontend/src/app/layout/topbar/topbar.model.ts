import { Params } from '@angular/router';
import { UserRole } from '../../core/auth/auth.models';

export type TopbarIcon = 'home' | 'grid' | 'bolt' | 'bell' | 'chevron' | 'users' | 'vehicle' | 'map' | 'shield' | 'message' | 'alert' | 'calendar' | 'organization' | 'terms' | 'logout' | 'menu';

export interface TopbarItem {
  readonly id: string;
  readonly label: string;
  readonly icon: TopbarIcon;
  readonly availability: 'available' | 'planned';
  readonly route: string | null;
  readonly queryParams?: Params;
}

export interface TopbarGroup {
  readonly id: string;
  readonly label: string;
  readonly description: string;
  readonly icon: TopbarIcon;
  readonly items: readonly TopbarItem[];
  readonly associatedRoutes?: readonly string[];
}

export interface TopbarIdentity {
  readonly avatarText: string;
  readonly displayName: string;
  readonly roleLabel: string;
  readonly headerLabel: string;
  readonly modulesLabel: string;
  readonly accountLabel: string;
  readonly accountPanelLabel: string;
}

export interface TopbarModel {
  readonly roles: readonly UserRole[];
  readonly identity: TopbarIdentity;
  readonly dashboard: TopbarItem;
  readonly groups: readonly TopbarGroup[];
  readonly quickActions: readonly TopbarItem[];
  readonly inbox: TopbarItem | null;
  readonly accountLinks: readonly TopbarItem[];
}

const available = (id: string, label: string, route: string, icon: TopbarIcon): TopbarItem => ({ id, label, route, icon, availability: 'available' });
const liveMap: TopbarItem = { id: 'live-map', label: 'Mapa en vivo', route: '/live-map', icon: 'map', availability: 'available' };

/** Navigation only: existing route guards and server authorization remain authoritative. */
export const ADMIN_TOPBAR_MODEL: TopbarModel = {
  roles: ['ADMIN'],
  identity: {
    avatarText: 'AR', displayName: 'Administrador Ruta Fija', roleLabel: 'Admin',
    headerLabel: 'Cabecera ADMIN', modulesLabel: 'Módulos ADMIN',
    accountLabel: 'Cuenta de administrador', accountPanelLabel: 'Cuenta ADMIN',
  },
  dashboard: available('dashboard', 'Dashboard', '/dashboard', 'home'),
  groups: [
    { id: 'people', label: 'Gestión de personas', description: 'Usuarios, invitaciones, solicitudes, grupos y conductores', icon: 'users', items: [
      available('users', 'Usuarios', '/users', 'users'),
      available('invitations', 'Invitaciones', '/invitations', 'message'),
      available('requests', 'Solicitudes', '/onboarding-requests', 'terms'),
      available('groups', 'Grupos', '/groups', 'users'),
      available('drivers', 'Conductores', '/drivers', 'users'),
    ] },
    { id: 'fleet', label: 'Gestión de flota', description: 'Vehículos', icon: 'vehicle', items: [available('vehicles', 'Vehículos', '/vehicles', 'vehicle')] },
    { id: 'operations', label: 'Operación', description: 'Asignaciones y rutas', icon: 'bolt', associatedRoutes: ['/service-types'], items: [available('assignments', 'Asignaciones', '/assignments', 'calendar'), available('routes', 'Rutas', '/routes', 'map')] },
    { id: 'monitoring', label: 'Monitoreo', description: 'Mapa en vivo', icon: 'map', items: [liveMap] },
    { id: 'control', label: 'Control', description: 'Auditoría y reportes', icon: 'shield', items: [available('audit', 'Auditoría', '/audit', 'shield'), available('reports', 'Reportes', '/reports', 'terms')] },
    { id: 'messages', label: 'Mensajes', description: 'Comunicados', icon: 'message', items: [available('announcements', 'Comunicados', '/announcements', 'message')] },
    { id: 'events', label: 'Eventos', description: 'Incidencias', icon: 'alert', items: [available('incidents', 'Incidencias', '/incidents', 'alert')] },
  ],
  quickActions: [
    { ...available('new-assignment', 'Asignación', '/assignments', 'calendar'), queryParams: { action: 'new' } },
    { ...liveMap, id: 'live-monitoring', label: 'Monitoreo en vivo' },
  ],
  inbox: available('inbox', 'Bandeja', '/notices', 'bell'),
  accountLinks: [available('organization', 'Organización', '/organization', 'organization'), available('admin-terms', 'Términos ADMIN', '/admin-terms', 'terms')],
};

export const SUPER_ADMIN_TOPBAR_MODEL: TopbarModel = {
  roles: ['SUPER_ADMIN'],
  identity: {
    avatarText: 'SA', displayName: 'Superadministrador Ruta Fija', roleLabel: 'Superadministrador',
    headerLabel: 'Cabecera de plataforma', modulesLabel: 'Módulos de plataforma',
    accountLabel: 'Cuenta de superadministrador', accountPanelLabel: 'Cuenta de plataforma',
  },
  dashboard: available('dashboard', 'Dashboard', '/dashboard', 'home'),
  groups: [
    {
      id: 'platform-administration', label: 'Administración de plataforma',
      description: 'Organizaciones y cuentas ADMIN', icon: 'organization',
      items: [
        available('organizations', 'Organizaciones', '/organizations', 'organization'),
        available('platform-admins', 'Cuentas ADMIN', '/platform-admins', 'users'),
      ],
    },
    {
      id: 'platform-control', label: 'Control de plataforma',
      description: 'Auditoría de plataforma', icon: 'shield',
      items: [available('platform-audit', 'Auditoría de plataforma', '/platform-audit', 'shield')],
    },
  ],
  quickActions: [],
  inbox: null,
  accountLinks: [],
};

/** A navigation projection, never an authorization grant or an ADMIN fallback. */
export function selectTopbarModel(role: string | null | undefined): TopbarModel | null {
  switch (role) {
    case 'ADMIN': return ADMIN_TOPBAR_MODEL;
    case 'SUPER_ADMIN': return SUPER_ADMIN_TOPBAR_MODEL;
    default: return null;
  }
}
