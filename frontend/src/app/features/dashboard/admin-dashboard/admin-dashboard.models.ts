/** Presentation-only contract. No authentication, network or business mutations. */
export type DashboardTone = 'blue' | 'green' | 'amber' | 'red' | 'violet' | 'gray';
export type DashboardPanelState = 'ready' | 'empty' | 'loading' | 'refreshing' | 'unavailable' | 'forbidden' | 'invalid' | 'stale';
export type DashboardIcon = 'driver' | 'vehicle' | 'calendar' | 'play' | 'alert' | 'fleet' | 'chart' | 'map' | 'bell' | 'arrow' | 'export' | 'check' | 'shield' | 'close';

export interface DashboardMetric {
  readonly id: string;
  readonly label: string;
  readonly value: number | null;
  readonly supporting: string;
  readonly icon: DashboardIcon;
  readonly tone: DashboardTone;
  readonly destination: string;
  readonly state: DashboardPanelState;
}

export interface DashboardSlice {
  readonly label: string;
  readonly value: number;
  readonly tone: DashboardTone;
}

export interface DashboardDonut {
  readonly id: string;
  readonly label: string;
  readonly total: number;
  readonly center: string;
  readonly centerLabel: string;
  readonly segments: readonly DashboardSlice[];
  readonly legend: readonly DashboardSlice[];
  readonly emptyText: string;
  readonly state: DashboardPanelState;
}

export interface DashboardPriority {
  readonly code: string;
  readonly label: string;
  readonly count: number;
  readonly destination: string;
}

export interface DashboardRoutePoint {
  readonly key: string;
  readonly label: string;
  readonly x: number;
  readonly y: number;
}

export interface DashboardRouteCatalog {
  readonly state: DashboardPanelState;
  readonly options: readonly { readonly id: string; readonly name: string }[];
  readonly selectedId: string;
  readonly page: number;
  readonly hasNext: boolean;
}

export interface AdminDashboardViewModel {
  readonly organization: string;
  readonly operationalDate: string;
  readonly selectedDate: string;
  readonly zoneId: string;
  readonly updatedLabel: string;
  readonly sessionLabel: string;
  readonly dateContextAvailable?: boolean;
  readonly timestamps?: Readonly<Record<'availability' | 'operation' | 'incidents' | 'drivers' | 'notices' | 'route', string>>;
  readonly coverageNotice?: string;
  readonly prioritiesState: DashboardPanelState;
  readonly priorities: readonly DashboardPriority[];
  readonly metrics: readonly DashboardMetric[];
  readonly fleet: DashboardDonut;
  readonly operation: DashboardDonut;
  readonly incidents: DashboardDonut;
  readonly route: {
    readonly state: DashboardPanelState;
    readonly name: string;
    readonly revision: number;
    readonly points: readonly DashboardRoutePoint[];
    readonly segments?: readonly (readonly DashboardRoutePoint[])[];
    readonly notice?: string;
  };
  readonly routeCatalog?: DashboardRouteCatalog;
  readonly driversState: DashboardPanelState;
  readonly drivers: readonly { readonly code: string; readonly status: string; readonly active: boolean; readonly tone?: DashboardTone }[];
  readonly driverTotal?: number | null;
  readonly noticesState: DashboardPanelState;
  readonly noticeTotal: number | null;
  readonly notices: readonly { readonly id: string; readonly title: string; readonly reference: string; readonly time: string; readonly read: boolean }[];
  readonly activity?: { readonly state: DashboardPanelState; readonly items: readonly { readonly id: string; readonly label: string; readonly time: string }[] };
}
