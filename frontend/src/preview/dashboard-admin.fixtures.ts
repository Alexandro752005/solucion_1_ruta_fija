/** Synthetic review fixtures. Imported by the preview entry point and tests only. */
import { AdminDashboardViewModel, DashboardPanelState, DashboardSlice } from '../app/features/dashboard/admin-dashboard/admin-dashboard.models';

export type DashboardPreviewScenario = 'normal' | 'activity' | 'critical' | 'unavailable' | 'loading';

export function dashboardFixture(scenario: DashboardPreviewScenario = 'normal', selectedDate = '2026-10-26'): AdminDashboardViewModel {
  const state: DashboardPanelState = scenario === 'unavailable' || scenario === 'loading' ? scenario : 'ready';
  const activity = scenario === 'activity' || scenario === 'critical';
  const fleet: readonly DashboardSlice[] = [
    { label: 'Disponibles', value: activity ? 4 : 2, tone: 'green' },
    { label: 'En servicio', value: activity ? 2 : 0, tone: 'blue' },
    { label: 'Atención segura', value: activity ? 1 : 0, tone: 'red' },
    { label: 'En mantenimiento', value: activity ? 1 : 0, tone: 'amber' },
    { label: 'Inactivos', value: activity ? 2 : 0, tone: 'gray' },
  ];
  const operation: readonly DashboardSlice[] = [
    { label: 'Completados', value: activity ? 4 : 0, tone: 'blue' },
    { label: 'Pendientes', value: activity ? 2 : 0, tone: 'amber' },
    { label: 'Cancelados', value: activity ? 2 : 0, tone: 'gray' },
    { label: 'Interrumpidos', value: activity ? 1 : 0, tone: 'red' },
    { label: 'Relevados', value: activity ? 1 : 0, tone: 'violet' },
  ];
  const incidents: readonly DashboardSlice[] = [
    { label: 'Abiertas', value: activity ? 1 : 0, tone: 'red' },
    { label: 'En seguimiento', value: activity ? 1 : 0, tone: 'amber' },
    { label: 'Resueltas', value: activity ? 3 : 0, tone: 'green' },
    { label: 'Cerradas por conductor', value: activity ? 1 : 0, tone: 'gray' },
  ];
  const critical = scenario === 'critical';
  const definitions = [
    ['CRITICAL_INCIDENTS', 'Incidencias críticas abiertas', critical ? 1 : 0, '/incidents'],
    ['SAFETY_ATTENTION', 'Recursos con atención segura', critical ? 1 : 0, '/assignments'],
    ['UPCOMING_UNACCEPTED', 'Servicios próximos sin aceptación', critical ? 2 : 0, '/assignments'],
    ['PENDING_REASSIGNMENT', 'Servicios por reasignar', 0, '/assignments'],
    ['EXPIRED_PENDING_OFFERS', 'Ofertas vencidas por regularizar', 0, '/assignments'],
    ['EXPIRING_OFFERS', 'Ofertas próximas a vencer', 0, '/assignments'],
    ['OPEN_INCIDENTS', 'Incidencias abiertas', activity ? 8 : 0, '/incidents'],
    ['ONBOARDING_REVIEW', 'Solicitudes por revisar', 0, '/onboarding-requests'],
  ] as const;
  return {
    organization: 'Ruta Fija Demo', operationalDate: '2026-10-26', selectedDate, zoneId: 'America/Lima',
    updatedLabel: '26 oct 2026, 10:00 · ejemplo', sessionLabel: 'Sesión de ejemplo', prioritiesState: state,
    priorities: definitions.map(([code, label, count, destination]) => ({ code, label, count, destination })),
    metrics: [
      { id: 'DA-K01', label: 'Conductores disponibles', value: state === 'ready' ? 2 : null, supporting: `de ${activity ? 4 : 2} registrados`, icon: 'driver', tone: 'blue', destination: '/drivers', state },
      { id: 'DA-K02', label: 'Vehículos disponibles', value: state === 'ready' ? activity ? 4 : 2 : null, supporting: `de ${activity ? 10 : 2} registrados`, icon: 'vehicle', tone: 'green', destination: '/vehicles', state },
      { id: 'DA-K03', label: 'Asignaciones futuras', value: state === 'ready' ? activity ? 3 : 0 : null, supporting: 'programadas', icon: 'calendar', tone: 'amber', destination: '/assignments', state },
      { id: 'DA-K04', label: 'Servicios en curso', value: state === 'ready' ? activity ? 2 : 0 : null, supporting: 'en ejecución', icon: 'play', tone: 'violet', destination: '/assignments', state },
      { id: 'DA-K05', label: 'Incidencias abiertas', value: state === 'ready' ? activity ? 8 : 0 : null, supporting: 'pendientes de gestión', icon: 'alert', tone: 'red', destination: '/incidents', state },
    ],
    fleet: { id: 'DA-P01', label: 'Estado de la flota', total: activity ? 10 : 2, center: activity ? '10' : '2', centerLabel: 'Vehículos', segments: fleet, legend: fleet, emptyText: 'Sin vehículos registrados', state },
    operation: { id: 'DA-P02', label: 'Resumen de operación', total: activity ? 10 : 0, center: activity ? '40.0 %' : '—', centerLabel: 'Cumplimiento', segments: [{ label: 'Completados', value: activity ? 4 : 0, tone: 'blue' }, { label: 'No completados', value: activity ? 6 : 0, tone: 'gray' }], legend: operation, emptyText: 'Sin servicios programados', state },
    incidents: { id: 'DA-P04', label: 'Incidencias por estado', total: activity ? 6 : 0, center: activity ? '6' : '0', centerLabel: 'Incidencias', segments: incidents, legend: incidents, emptyText: 'Sin incidencias reportadas en el día', state },
    route: { state, name: 'Ruta de ejemplo', revision: 3, points: [{ key: 'start', label: 'Inicio', x: 24, y: 116 }, { key: 'stop-1', label: 'Parada 1', x: 112, y: 102 }, { key: 'stop-2', label: 'Parada 2', x: 208, y: 68 }, { key: 'end', label: 'Terminal', x: 314, y: 34 }] },
    driversState: state,
    drivers: [
      { code: 'CONDUCTOR-00000000-0000-4000-8000-000000000001', status: 'Disponible', active: true },
      { code: 'CONDUCTOR-00000000-0000-4000-8000-000000000002', status: 'Disponible', active: true },
    ],
    noticesState: state, noticeTotal: activity ? 2 : 0,
    notices: activity ? [
      { id: 'notice-example-1', title: 'Asignación actualizada', reference: 'Servicio de ejemplo pendiente de revisión.', time: '09:48', read: false },
      { id: 'notice-example-2', title: 'Comunicado disponible', reference: 'Consulta la bandeja para revisar su contenido.', time: '09:35', read: true },
    ] : [],
  };
}
