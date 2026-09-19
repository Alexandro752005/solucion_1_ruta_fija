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

  readonly actionOptions = [
    'LOGIN_SUCCESS', 'LOGOUT', 'USER_CREATED', 'USER_UPDATED', 'USER_ACTIVATED', 'USER_DISABLED',
    'GROUP_CREATED', 'GROUP_UPDATED', 'DRIVER_CREATED', 'DRIVER_UPDATED', 'DRIVER_AVAILABILITY_CHANGED',
    'VEHICLE_CREATED', 'VEHICLE_UPDATED', 'VEHICLE_STATUS_CHANGED', 'ASSIGNMENT_SCHEDULED',
    'ASSIGNMENT_UPDATED', 'ASSIGNMENT_RESERVED', 'ASSIGNMENT_STARTED', 'ASSIGNMENT_COMPLETED',
    'ASSIGNMENT_CANCELLED', 'INCIDENT_REPORTED_FROM_CRM', 'INCIDENT_FOLLOWED_UP', 'ANNOUNCEMENT_PUBLISHED',
  ] as const;
  readonly entityOptions = ['APP_USER', 'TRANSPORT_GROUP', 'DRIVER', 'VEHICLE', 'ASSIGNMENT', 'INCIDENT', 'ANNOUNCEMENT'] as const;

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
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
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
    this.api.get(event.id).pipe(finalize(() => this.loadingDetail.set(false))).subscribe({
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
    } as Record<string, string>)[action] ?? 'Actividad registrada';
  }

  entityLabel(entityType: string | null | undefined): string {
    return ({ APP_USER: 'Usuarios', TRANSPORT_GROUP: 'Grupos', DRIVER: 'Conductores', VEHICLE: 'Vehículos', ASSIGNMENT: 'Asignaciones', INCIDENT: 'Incidencias', ANNOUNCEMENT: 'Comunicados' } as Record<string, string>)[entityType ?? ''] ?? 'Acceso';
  }

  private load(page = this.page()): void {
    const value = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api.list({
      page,
      size: 20,
      sort: 'occurredAt,desc',
      action: value.action.trim() || undefined,
      entityType: value.entityType.trim() || undefined,
      from: asInstant(value.from),
      to: asInstant(value.to, true),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
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
