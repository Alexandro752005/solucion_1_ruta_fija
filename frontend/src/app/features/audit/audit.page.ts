import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuditApiService } from '../../core/audit/audit-api.service';
import { AuditEvent } from '../../core/audit/audit.models';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

function asInstant(value: string, endOfDay = false): string | undefined {
  if (!value) {
    return undefined;
  }
  const date = new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

@Component({
  selector: 'rf-audit-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, PaginationComponent],
  templateUrl: './audit.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditPage {
  private readonly api = inject(AuditApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
  readonly platformScope = computed(() => this.session.hasAnyRole(['SUPER_ADMIN']));

  readonly actionOptions = [
    'LOGIN_SUCCESS', 'LOGOUT', 'USER_CREATED', 'USER_UPDATED', 'USER_ACTIVATED', 'USER_DISABLED',
    'GROUP_CREATED', 'GROUP_UPDATED', 'DRIVER_CREATED', 'DRIVER_UPDATED', 'DRIVER_AVAILABILITY_CHANGED',
    'VEHICLE_CREATED', 'VEHICLE_UPDATED', 'VEHICLE_STATUS_CHANGED', 'ASSIGNMENT_SCHEDULED',
    'ASSIGNMENT_UPDATED', 'ASSIGNMENT_RESERVED', 'ASSIGNMENT_STARTED', 'ASSIGNMENT_COMPLETED',
    'ASSIGNMENT_CANCELLED', 'INCIDENT_REPORTED_FROM_CRM', 'INCIDENT_FOLLOWED_UP', 'ANNOUNCEMENT_PUBLISHED',
    'SECURITY_ACCESS_DENIED','LOGIN_FAILURE','MOBILE_LOGIN_DENIED','ADMIN_CREATED','ADMIN_ACTIVATED',
    'ADMIN_REPLACEMENT_COMPLETED','ADMIN_TERMS_ACCEPTED','ORGANIZATION_CREATED','ORGANIZATION_UPDATED',
    'ASSIGNMENT_MOBILE_ACCEPTED','ASSIGNMENT_MOBILE_REJECTED','ASSIGNMENT_RESPONSE_EXPIRED',
    'ASSIGNMENT_MOBILE_STARTED','ASSIGNMENT_MOBILE_COMPLETED','DRIVER_QUALIFICATION_CHANGED',
    'SERVICE_INTERRUPTED','SERVICE_HANDOVER_ASSUMED','SERVICE_TIMES_CORRECTED','REPORT_EMITTED',
    'INCIDENT_ASSESSMENT','INCIDENT_RESPONSIBILITY','INCIDENT_RESOLUTION','MOBILE_PREFERENCES_CHANGED',
    'RETENTION_PLAN_REQUESTED','RETENTION_PLAN_EXECUTED',
  ] as const;
  readonly entityOptions = ['APP_USER', 'TRANSPORT_GROUP', 'DRIVER', 'VEHICLE', 'ASSIGNMENT', 'SCHEDULED_SERVICE', 'SERVICE', 'SERVICE_ATTENTION', 'INCIDENT', 'ANNOUNCEMENT', 'REPORT_EMISSION', 'HTTP_REQUEST', 'ORGANIZATION', 'RETENTION_PLAN'] as const;

  readonly events = signal<readonly AuditEvent[]>([]);
  readonly selected = signal<AuditEvent | null>(null);
  readonly detailOpen = signal(false);
  readonly loading = signal(true);
  readonly loadingDetail = signal(false);
  readonly page = signal(0);
  readonly totalItems = signal(0);
  readonly totalPages = signal(0);
  readonly error = signal<UiError | null>(null);
  readonly organizationName = computed(
    () => this.platformScope() ? 'Ámbito de plataforma' : this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );

  readonly form = new FormGroup({
    action: new FormControl('', { nonNullable: true }),
    entityType: new FormControl('', { nonNullable: true }),
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.load();
  }

  applyFilters(): void {
    this.selected.set(null);
    this.detailOpen.set(false);
    this.load(0);
  }

  clearFilters(): void {
    this.form.reset({ action: '', entityType: '', from: '', to: '' });
    this.selected.set(null);
    this.detailOpen.set(false);
    this.load(0);
  }

  select(event: AuditEvent): void {
    this.detailOpen.set(true);
    this.selected.set(null);
    this.loadingDetail.set(true);
    this.error.set(null);
    (this.platformScope() ? this.api.platformGet(event.id) : this.api.get(event.id)).pipe(finalize(() => this.loadingDetail.set(false))).subscribe({
      next: (detail) => this.selected.set(detail),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el detalle de auditoría.')),
    });
  }

  closeDetail(): void {
    if (!this.loadingDetail()) {
      this.detailOpen.set(false);
      this.selected.set(null);
    }
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'medium' });
  }

  metadataLabel(event: AuditEvent): string {
    const entries = Object.entries(event.metadata);
    return entries.length === 0 ? 'Sin metadatos adicionales' : JSON.stringify(event.metadata, null, 2);
  }

  actionLabel(action: string): string {
    return ({
      LOGIN_SUCCESS: 'Inicio de sesión', LOGOUT: 'Cierre de sesión', USER_CREATED: 'Usuario creado',
      USER_UPDATED: 'Usuario actualizado', USER_ACTIVATED: 'Usuario activado', USER_DISABLED: 'Usuario desactivado',
      GROUP_CREATED: 'Grupo creado', GROUP_UPDATED: 'Grupo actualizado', DRIVER_CREATED: 'Conductor registrado',
      DRIVER_UPDATED: 'Conductor actualizado', DRIVER_ACTIVATED: 'Conductor activado', DRIVER_DISABLED: 'Conductor desactivado',
      DRIVER_AVAILABILITY_CHANGED: 'Disponibilidad de conductor actualizada', VEHICLE_CREATED: 'Vehículo registrado',
      VEHICLE_UPDATED: 'Vehículo actualizado', VEHICLE_STATUS_CHANGED: 'Estado de vehículo actualizado',
      ASSIGNMENT_SCHEDULED: 'Asignación programada', ASSIGNMENT_UPDATED: 'Asignación actualizada',
      ASSIGNMENT_RESERVED: 'Recursos reservados', ASSIGNMENT_STARTED: 'Servicio iniciado',
      ASSIGNMENT_COMPLETED: 'Servicio completado', ASSIGNMENT_CANCELLED: 'Asignación cancelada',
      INCIDENT_REPORTED_FROM_CRM: 'Incidencia registrada', INCIDENT_FOLLOWED_UP: 'Incidencia actualizada',
      ANNOUNCEMENT_PUBLISHED: 'Comunicado publicado',
      SECURITY_ACCESS_DENIED: 'Acceso denegado', LOGIN_FAILURE: 'Autenticación rechazada', MOBILE_LOGIN_DENIED: 'Acceso móvil denegado',
      ADMIN_CREATED: 'Alta ADMIN pendiente', ADMIN_ACTIVATED: 'ADMIN habilitado', ADMIN_REPLACEMENT_COMPLETED: 'Reemplazo ADMIN completado',
      ADMIN_TERMS_ACCEPTED: 'Aceptación expresa de términos', ORGANIZATION_CREATED: 'Organización creada', ORGANIZATION_UPDATED: 'Organización actualizada',
      ASSIGNMENT_MOBILE_ACCEPTED: 'Oferta aceptada por CONDUCTOR', ASSIGNMENT_MOBILE_REJECTED: 'Oferta rechazada por CONDUCTOR',
      ASSIGNMENT_RESPONSE_EXPIRED: 'Oferta vencida', ASSIGNMENT_MOBILE_STARTED: 'Servicio iniciado por CONDUCTOR', ASSIGNMENT_MOBILE_COMPLETED: 'Ruta finalizada por CONDUCTOR',
      DRIVER_QUALIFICATION_CHANGED: 'Habilitación o expediente revisado', SERVICE_INTERRUPTED: 'Servicio interrumpido sin cumplimiento',
      SERVICE_HANDOVER_ASSUMED: 'Relevo asumido', SERVICE_TIMES_CORRECTED: 'Tiempos corregidos con originales conservados', REPORT_EMITTED: 'Reporte emitido',
      INCIDENT_ASSESSMENT: 'Gravedad de incidencia revisada', INCIDENT_RESPONSIBILITY: 'Responsable de incidencia designado', INCIDENT_RESOLUTION: 'Incidencia cerrada',
      MOBILE_PREFERENCES_CHANGED: 'Preferencias propias actualizadas', RETENTION_PLAN_REQUESTED: 'Plan exacto de retiro solicitado', RETENTION_PLAN_EXECUTED: 'Plan exacto de retiro ejecutado',
    } as Record<string, string>)[action] ?? action;
  }

  entityLabel(entityType: string | null | undefined): string {
    return ({ APP_USER: 'Usuarios', TRANSPORT_GROUP: 'Grupos', DRIVER: 'Conductores', VEHICLE: 'Vehículos', ASSIGNMENT: 'Ofertas', SCHEDULED_SERVICE: 'Servicios', SERVICE: 'Ejecución de servicios', SERVICE_ATTENTION: 'Atención segura', INCIDENT: 'Incidencias', ANNOUNCEMENT: 'Comunicados', REPORT_EMISSION:'Emisiones de reportes', HTTP_REQUEST:'Seguridad', ORGANIZATION:'Organizaciones', RETENTION_PLAN:'Retiro restringido' } as Record<string, string>)[entityType ?? ''] ?? 'Acceso';
  }

  private load(page = this.page()): void {
    const value = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    const query = {
      page,
      size: 20,
      sort: 'occurredAt,desc',
      action: value.action.trim() || undefined,
      entityType: value.entityType.trim() || undefined,
      from: asInstant(value.from),
      to: asInstant(value.to, true),
    };
    (this.platformScope() ? this.api.platformList(query) : this.api.list(query)).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.events.set(response.items);
        this.page.set(response.page);
        this.totalItems.set(response.totalItems);
        this.totalPages.set(response.totalPages);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron consultar los eventos de auditoría.')),
    });
  }
}
