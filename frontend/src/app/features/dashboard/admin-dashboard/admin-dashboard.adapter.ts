import { AdminDashboard } from '../../../core/dashboard/dashboard-api.service';
import { AvailabilityReport, IncidentReport, StatusCount } from '../../../core/operations/operations.models';
import { ServiceReportView } from '../../../core/reports/service-reports.models';
import { RouteStop, RouteSummary, RouteVersion, RouteView } from '../../../core/routes/routes-api.service';
import { Driver, PageResult } from '../../../core/management/management.models';
import { OperationNotice } from '../../../core/operations/communication-api.service';
import { AuditEvent } from '../../../core/audit/audit.models';
import { AdminDashboardViewModel, DashboardDonut, DashboardPanelState, DashboardPriority, DashboardRoutePoint, DashboardSlice, DashboardTone } from './admin-dashboard.models';

export interface DashboardSource<T> { readonly state: DashboardPanelState; readonly value: T | null; readonly consultedAt: string | null; }
export interface DashboardInputs {
  readonly organization: string;
  readonly userId: string;
  readonly selectedDay: string;
  readonly summary: DashboardSource<AdminDashboard>;
  readonly availability: DashboardSource<AvailabilityReport>;
  readonly services: DashboardSource<ServiceReportView>;
  readonly incidents: DashboardSource<IncidentReport>;
  readonly drivers: DashboardSource<PageResult<Driver>>;
  readonly notices: DashboardSource<PageResult<OperationNotice>>;
  readonly route: DashboardSource<RouteView>;
  readonly routeNotice: string;
  readonly activity: DashboardSource<readonly AuditEvent[]>;
}

export const PRIORITIES = [
  ['CRITICAL_INCIDENTS', 'Incidencias críticas abiertas', '/incidents'],
  ['SAFETY_ATTENTION', 'Recursos con atención segura', '/assignments'],
  ['UPCOMING_UNACCEPTED', 'Servicios próximos sin aceptación', '/assignments'],
  ['PENDING_REASSIGNMENT', 'Servicios por reasignar', '/assignments'],
  ['EXPIRED_PENDING_OFFERS', 'Ofertas vencidas por regularizar', '/assignments'],
  ['EXPIRING_OFFERS', 'Ofertas próximas a vencer', '/assignments'],
  ['OPEN_INCIDENTS', 'Incidencias abiertas', '/incidents'],
  ['ONBOARDING_REVIEW', 'Solicitudes por revisar', '/onboarding-requests'],
] as const;

export class InvalidDashboardResponse extends Error {
  constructor() { super('DASHBOARD_RESPONSE_INCOMPATIBLE'); }
}
function ensure(value: unknown): asserts value { if (!value) throw new InvalidDashboardResponse(); }
export function isCount(value: unknown): value is number { return Number.isSafeInteger(value) && (value as number) >= 0; }
export function isDay(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value + 'T00:00:00Z')) && new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) === value;
}
export function isInstant(value: unknown): value is string { return typeof value === 'string' && value.includes('T') && Number.isFinite(Date.parse(value)); }
export function isUuid(value: unknown): value is string { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value); }
export function distribution(rows: readonly StatusCount[]): readonly StatusCount[] {
  ensure(Array.isArray(rows));
  ensure(rows.every(row => row && typeof row.status === 'string' && row.status.length > 0 && isCount(row.total)));
  ensure(new Set(rows.map(row => row.status)).size === rows.length);
  ensure(isCount(rows.reduce((sum, row) => sum + row.total, 0)));
  return rows;
}
export function validateSummary(value: AdminDashboard): AdminDashboard {
  ensure(value && isInstant(value.generatedAt) && isInstant(value.upcomingUntil));
  ensure([value.servicesInProgress, value.activeDrivers, value.activeVehicles].every(isCount));
  ensure(Array.isArray(value.priorities) && value.priorities.length === PRIORITIES.length);
  ensure(PRIORITIES.every(([code, , route], index) => value.priorities.filter(row => row.code === code && row.order === index + 1 && row.route === route && isCount(row.count)).length === 1));
  return value;
}
export function dateContext(summary: AdminDashboard | null): { zoneId: string; operationalDate: string } | null {
  if (!summary || typeof summary.zoneId !== 'string' || !isDay(summary.operationalDate)) return null;
  try {
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: summary.zoneId, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(summary.generatedAt));
    const part = (key: string) => parts.find(value => value.type === key)?.value;
    if (`${part('year')}-${part('month')}-${part('day')}` !== summary.operationalDate) return null;
    return { zoneId: summary.zoneId, operationalDate: summary.operationalDate };
  } catch { return null; }
}
export function validateAvailability(value: AvailabilityReport): AvailabilityReport {
  ensure(value && isInstant(value.generatedAt)); distribution(value.drivers); distribution(value.vehicles);
  ensure([value.futureScheduledAssignments, value.assignmentsInService, value.openIncidents].every(isCount)); return value;
}
export function validateServices(value: ServiceReportView, day: string, zone: string): ServiceReportView {
  ensure(value && value.scope === 'ORGANIZATION' && value.from === day && value.to === day && value.zoneId === zone && isInstant(value.cutoffAt));
  const t = value.totals;
  ensure(t && [t.scheduled, t.completed, t.pending, t.cancelled, t.interrupted, t.transferred].every(isCount));
  ensure(t.scheduled === t.completed + t.pending + t.cancelled + t.interrupted + t.transferred);
  ensure(t.scheduled === 0 ? t.completionPercent === null : typeof t.completionPercent === 'number' && Number.isFinite(t.completionPercent) && Math.abs(t.completionPercent - t.completed / t.scheduled * 100) <= 0.000051);
  ensure(value.coverage && isCount(value.coverage.excludedLegacyOffers) && isCount(value.coverage.canonicalServicesWithoutFirstOffer)); return value;
}
export function validateIncidents(value: IncidentReport, day: string): IncidentReport {
  ensure(value && value.from === day && value.to === day); distribution(value.totalsByStatus); return value;
}
export function validatePage<T>(page: PageResult<T>, size: number): PageResult<T> {
  ensure(page && Array.isArray(page.items) && page.items.length <= size && isCount(page.totalItems) && page.totalItems >= page.items.length && page.page === 0); return page;
}
export function validateDrivers(page: PageResult<Driver>): PageResult<Driver> {
  validatePage(page, 2); ensure(page.items.every(row => isUuid(row.id) && typeof row.active === 'boolean' && typeof row.availabilityStatus === 'string')); return page;
}
export function validateNotices(page: PageResult<OperationNotice>, userId: string): PageResult<OperationNotice> {
  validatePage(page, 20); ensure(page.items.every(row => isUuid(row.id) && row.userId === userId && typeof row.eventType === 'string' && isInstant(row.createdAt) && (row.readAt == null || isInstant(row.readAt)))); return page;
}
export function validateRoutes(rows: readonly RouteSummary[]): readonly RouteSummary[] {
  ensure(Array.isArray(rows) && rows.length <= 100 && rows.every(row => isUuid(row.id) && typeof row.active === 'boolean' && typeof row.name === 'string')); return rows;
}
export function routePresentation(view: RouteView): AdminDashboardViewModel['route'] {
  ensure(view && isUuid(view.id) && Array.isArray(view.versions));
  const version = (view.versions as readonly RouteVersion[]).find(row => row.id === view.currentVersionId);
  if (!version) return { state: 'empty', name: view.name, revision: 0, points: [], segments: [], notice: 'La ruta no tiene una versión vigente disponible.' };
  ensure(isCount(version.revision) && version.revision > 0 && Array.isArray(version.stops));
  const stops: readonly RouteStop[] = version.stops;
  ensure(stops.every(stop => stop && typeof stop.key === 'string' && typeof stop.name === 'string'));
  const valid = stops.map((stop, index) => ({ stop, index })).filter(({ stop }) => typeof stop.latitude === 'number' && Number.isFinite(stop.latitude) && Math.abs(stop.latitude) <= 90 && typeof stop.longitude === 'number' && Number.isFinite(stop.longitude) && Math.abs(stop.longitude) <= 180);
  const xs = valid.map(({ stop }) => stop.longitude!); const ys = valid.map(({ stop }) => stop.latitude!);
  const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
  const points: DashboardRoutePoint[] = valid.map(({ stop }) => ({ key: stop.key, label: stop.name, x: maxX === minX ? 170 : 24 + (stop.longitude! - minX) / (maxX - minX) * 292, y: maxY === minY ? 75 : 120 - (stop.latitude! - minY) / (maxY - minY) * 90 }));
  const segments: DashboardRoutePoint[][] = [];
  valid.forEach((row, i) => { if (i === 0 || row.index !== valid[i - 1].index + 1) segments.push([]); segments[segments.length - 1].push(points[i]); });
  return { state: 'ready', name: version.name, revision: version.revision, points, segments: segments.filter(segment => segment.length >= 2), notice: segments.some(segment => segment.length >= 2) ? '' : 'La ruta no tiene coordenadas adyacentes suficientes.' };
}

const count = (rows: readonly StatusCount[], status: string) => rows.find(row => row.status === status)?.total ?? 0;
const total = (rows: readonly StatusCount[]) => rows.reduce((sum, row) => sum + row.total, 0);
const number = (value: number) => new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
const fleetDefinitions = [['DISPONIBLE', 'Disponibles', 'green'], ['EN_SERVICIO', 'En servicio', 'blue'], ['ATENCION', 'Atención segura', 'red'], ['MANTENIMIENTO', 'En mantenimiento', 'amber'], ['INACTIVO', 'Inactivos', 'gray']] as const;
const incidentDefinitions = [['OPEN', 'Abiertas', 'red'], ['FOLLOW_UP', 'En seguimiento', 'amber'], ['RESOLVED', 'Resueltas', 'green'], ['CLOSED_BY_DRIVER', 'Cerradas por conductor', 'gray']] as const;
function slices(rows: readonly StatusCount[], definitions: readonly (readonly [string, string, DashboardTone])[]): DashboardSlice[] {
  const known = new Set(definitions.map(row => row[0]));
  const result: DashboardSlice[] = definitions.map(([status, label, tone]) => ({ label, value: count(rows, status), tone }));
  const other = total(rows.filter(row => !known.has(row.status)));
  if (other > 0) result.push({ label: 'Otros estados', value: other, tone: 'gray' }); return result;
}
function time(value: string | null | undefined, zone: string): string {
  return isInstant(value) ? new Intl.DateTimeFormat('es-PE', { timeZone: zone, dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) + (zone === 'UTC' ? ' UTC' : '') : 'Sin consulta confirmada';
}
const noticeTitle = (event: string) => event.startsWith('assignment.') ? 'Cambio de oferta' : event.startsWith('incident.') ? 'Cambio de incidencia' : event.startsWith('service.') ? 'Cambio de servicio' : ({ 'announcement.published': 'Comunicado publicado', 'announcement.read.pending': 'Lecturas de comunicado pendientes', 'driver.suspended': 'Suspensión operativa', 'driver.status.changed': 'Cambio de disponibilidad' } as Record<string, string>)[event] ?? 'Aviso operativo';

export function dashboardPresentation(input: DashboardInputs): AdminDashboardViewModel {
  const summary = input.summary.value, availability = input.availability.value;
  const context = dateContext(summary), zone = context?.zoneId ?? 'UTC';
  const fleetRows = availability?.vehicles ?? [], driverRows = availability?.drivers ?? [], incidentRows = input.incidents.value?.totalsByStatus ?? [];
  const fleetLegend = slices(fleetRows, fleetDefinitions), incidentLegend = slices(incidentRows, incidentDefinitions);
  const service = input.services.value, t = service?.totals;
  const operationDefinitions = [['completed', 'Completados', 'blue'], ['pending', 'Pendientes', 'amber'], ['cancelled', 'Cancelados', 'gray'], ['interrupted', 'Interrumpidos', 'red'], ['transferred', 'Relevados', 'violet']] as const;
  const legend: DashboardSlice[] = operationDefinitions.map(([key, label, tone]) => ({ label, value: t ? t[key] : 0, tone }));
  const ring = (id: string, label: string, rows: DashboardSlice[], state: DashboardPanelState, centerLabel: string, emptyText: string): DashboardDonut => ({ id, label, total: rows.reduce((sum, row) => sum + row.value, 0), center: String(rows.reduce((sum, row) => sum + row.value, 0)), centerLabel, legend: rows, segments: rows, state, emptyText });
  let route: AdminDashboardViewModel['route'] = { state: input.route.state, name: '', revision: 0, points: [], segments: [], notice: input.routeNotice };
  if (input.route.value) route = { ...routePresentation(input.route.value), state: input.route.state };
  const driverStatus: Readonly<Record<string, readonly [string, DashboardTone]>> = { DISPONIBLE: ['Disponible', 'green'], RESERVADO: ['Reservado', 'amber'], EN_SERVICIO: ['En servicio', 'blue'], ATENCION: ['Atención segura', 'red'], DESCANSO: ['Descanso', 'gray'], NO_DISPONIBLE: ['No disponible', 'gray'] };
  const coverage = service?.coverage;
  const reference = (notice: OperationNotice) => {
    for (const [key, prefix] of [['serviceId', 'SERVICIO'], ['assignmentId', 'OFERTA'], ['incidentId', 'INCIDENCIA'], ['announcementId', 'COMUNICADO']]) {
      const value = notice.references?.[key]; if (isUuid(value)) return `${prefix}-${value}`;
    }
    return 'Aviso de tu bandeja personal';
  };
  const priorities: DashboardPriority[] = summary ? PRIORITIES.map(([code, label, destination]) => ({ code, label, destination, count: summary.priorities.find(row => row.code === code)!.count })) : [];
  return {
    organization: input.organization, operationalDate: context?.operationalDate ?? '', selectedDate: input.selectedDay, zoneId: context?.zoneId ?? 'Zona no disponible', dateContextAvailable: context !== null,
    updatedLabel: summary ? time(summary.generatedAt, zone) : 'Sin consulta confirmada', sessionLabel: 'Sesión activa', prioritiesState: input.summary.state, priorities,
    metrics: [
      { id: 'DA-K01', label: 'Conductores disponibles', value: availability ? count(driverRows, 'DISPONIBLE') : null, supporting: `de ${total(driverRows)} registrados`, icon: 'driver', tone: 'blue', destination: '/drivers', state: input.availability.state },
      { id: 'DA-K02', label: 'Vehículos disponibles', value: availability ? count(fleetRows, 'DISPONIBLE') : null, supporting: `de ${total(fleetRows)} registrados`, icon: 'vehicle', tone: 'green', destination: '/vehicles', state: input.availability.state },
      { id: 'DA-K03', label: 'Asignaciones futuras', value: availability?.futureScheduledAssignments ?? null, supporting: 'programadas', icon: 'calendar', tone: 'amber', destination: '/assignments', state: input.availability.state },
      { id: 'DA-K04', label: 'Servicios en curso', value: summary?.servicesInProgress ?? null, supporting: 'en ejecución', icon: 'play', tone: 'violet', destination: '/assignments', state: input.summary.state },
      { id: 'DA-K05', label: 'Incidencias abiertas', value: availability?.openIncidents ?? null, supporting: 'pendientes de gestión', icon: 'alert', tone: 'red', destination: '/incidents', state: input.availability.state },
    ],
    fleet: ring('DA-P01', 'Estado de la flota', fleetLegend, input.availability.state, 'Vehículos', 'Sin vehículos registrados'),
    operation: { id: 'DA-P02', label: 'Resumen de operación', total: t?.scheduled ?? 0, center: t?.completionPercent == null ? '—' : number(t.completionPercent) + ' %', centerLabel: 'Cumplimiento', legend, segments: [{ label: 'Completados', value: t?.completed ?? 0, tone: 'blue' }, { label: 'No completados', value: t ? t.scheduled - t.completed : 0, tone: 'gray' }], emptyText: 'Sin servicios programados', state: input.services.state },
    incidents: ring('DA-P04', 'Incidencias por estado', incidentLegend, input.incidents.state, 'Incidencias', 'Sin incidencias reportadas en el día'),
    route,
    driversState: input.drivers.state, driverTotal: input.drivers.value?.totalItems ?? null,
    drivers: (input.drivers.value?.items ?? []).map(row => ({ code: 'CONDUCTOR-' + row.id, status: driverStatus[row.availabilityStatus]?.[0] ?? 'Estado no disponible', tone: driverStatus[row.availabilityStatus]?.[1] ?? 'gray', active: row.active })),
    noticesState: input.notices.state, noticeTotal: input.notices.value?.totalItems ?? null,
    notices: (input.notices.value?.items ?? []).slice(0, 2).map(row => ({ id: row.id, title: noticeTitle(row.eventType), reference: reference(row), time: time(row.createdAt, zone), read: !!row.readAt })),
    timestamps: { availability: 'Corte ' + time(availability?.generatedAt, zone), operation: 'Corte ' + time(service?.cutoffAt, zone), incidents: 'Consultado ' + time(input.incidents.consultedAt, zone), drivers: 'Consultado ' + time(input.drivers.consultedAt, zone), notices: 'Consultado ' + time(input.notices.consultedAt, zone), route: 'Consultado ' + time(input.route.value?.observedAt ?? input.route.consultedAt, zone) },
    coverageNotice: coverage && (coverage.excludedLegacyOffers || coverage.canonicalServicesWithoutFirstOffer) ? `${coverage.excludedLegacyOffers} ofertas legadas excluidas; ${coverage.canonicalServicesWithoutFirstOffer} servicios sin primera oferta.` : '',
    activity: { state: input.activity.state, items: (input.activity.value ?? []).map(row => ({ id: row.id, label: ({ LOGIN_SUCCESS: 'Inicio de sesión', LOGOUT: 'Cierre de sesión', ASSIGNMENT_STARTED: 'Servicio iniciado', ASSIGNMENT_COMPLETED: 'Servicio completado', INCIDENT_REPORTED_FROM_CRM: 'Incidencia registrada' } as Record<string, string>)[row.action] ?? 'Actividad registrada', time: time(row.occurredAt, zone) })) },
  };
}
