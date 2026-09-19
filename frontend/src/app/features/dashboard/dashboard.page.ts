import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuditApiService } from '../../core/audit/audit-api.service';
import { AuditEvent } from '../../core/audit/audit.models';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { AvailabilityReport } from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusIndicatorComponent } from '../../shared/ui/status-indicator.component';

@Component({
  selector: 'rf-dashboard-page',
  imports: [RouterLink, PageHeaderComponent, StatusIndicatorComponent],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  readonly session = inject(AuthSessionService);
  private readonly operationsApi = inject(OperationsApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly auditApi = inject(AuditApiService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdministrator = computed(() => this.session.hasAnyRole(['ADMINISTRADOR']));
  readonly isCoordinator = computed(() => this.session.hasAnyRole(['COORDINADOR']));
  readonly isSuperAdmin = computed(() => this.session.hasAnyRole(['SUPER_ADMIN']));
  readonly organizationContext = computed(() => {
    if (this.isSuperAdmin()) {
      return null;
    }

    return this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada';
  });
  readonly description = computed(() => {
    if (this.isAdministrator()) {
      return 'Revisa el estado operativo actual de la organizaci\u00f3n.';
    }
    if (this.isCoordinator()) {
      return 'Accede a los recursos y actividades que coordinas en la organizaci\u00f3n.';
    }
    return 'Consulta el estado general disponible para la administraci\u00f3n de la plataforma.';
  });

  readonly availability = signal<AvailabilityReport | null>(null);
  readonly loadingAvailability = signal(false);
  readonly availabilityError = signal(false);
  readonly recentActivity = signal<readonly AuditEvent[]>([]);
  readonly loadingActivity = signal(false);
  readonly activityError = signal(false);
  readonly organizationTotal = signal<number | null>(null);
  readonly loadingOrganizations = signal(false);
  readonly organizationsError = signal(false);

  constructor() {
    if (this.isAdministrator()) {
      this.loadAvailability();
      this.loadRecentActivity();
      this.realtime.connect();
      this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        this.loadAvailability();
        this.loadRecentActivity();
      });
      this.destroyRef.onDestroy(() => this.realtime.disconnect());
    } else if (this.isSuperAdmin()) {
      this.loadOrganizationTotal();
    }
  }

  countByStatus(items: readonly { readonly status: string; readonly total: number }[], status: string): number {
    return items.find((item) => item.status === status)?.total ?? 0;
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
  }

  actionLabel(action: string): string {
    const labels: Readonly<Record<string, string>> = {
      LOGIN_SUCCESS: 'Inicio de sesi\u00f3n',
      LOGOUT: 'Cierre de sesi\u00f3n',
      USER_CREATED: 'Usuario creado',
      USER_UPDATED: 'Usuario actualizado',
      USER_ACTIVATED: 'Usuario activado',
      USER_DISABLED: 'Usuario desactivado',
      GROUP_CREATED: 'Grupo creado',
      GROUP_UPDATED: 'Grupo actualizado',
      DRIVER_CREATED: 'Conductor registrado',
      DRIVER_UPDATED: 'Conductor actualizado',
      DRIVER_ACTIVATED: 'Conductor activado',
      DRIVER_DISABLED: 'Conductor desactivado',
      DRIVER_AVAILABILITY_CHANGED: 'Disponibilidad de conductor actualizada',
      VEHICLE_CREATED: 'Veh\u00edculo registrado',
      VEHICLE_UPDATED: 'Veh\u00edculo actualizado',
      VEHICLE_STATUS_CHANGED: 'Estado de veh\u00edculo actualizado',
      ASSIGNMENT_SCHEDULED: 'Asignaci\u00f3n programada',
      ASSIGNMENT_UPDATED: 'Asignaci\u00f3n actualizada',
      ASSIGNMENT_RESERVED: 'Recursos reservados',
      ASSIGNMENT_STARTED: 'Servicio iniciado',
      ASSIGNMENT_COMPLETED: 'Servicio completado',
      ASSIGNMENT_CANCELLED: 'Asignaci\u00f3n cancelada',
      INCIDENT_REPORTED_FROM_CRM: 'Incidencia registrada',
      INCIDENT_FOLLOWED_UP: 'Incidencia actualizada',
      ANNOUNCEMENT_PUBLISHED: 'Comunicado publicado',
    };
    return labels[action] ?? 'Actividad registrada';
  }

  entityLabel(entityType: string | null | undefined): string {
    const labels: Readonly<Record<string, string>> = {
      APP_USER: 'Usuarios',
      TRANSPORT_GROUP: 'Grupos',
      DRIVER: 'Conductores',
      VEHICLE: 'Veh\u00edculos',
      ASSIGNMENT: 'Asignaciones',
      INCIDENT: 'Incidencias',
      ANNOUNCEMENT: 'Comunicados',
    };
    return entityType ? labels[entityType] ?? 'Sistema' : 'Acceso';
  }

  private loadAvailability(): void {
    this.loadingAvailability.set(true);
    this.availabilityError.set(false);
    this.operationsApi.availabilityReport().pipe(finalize(() => this.loadingAvailability.set(false))).subscribe({
      next: (report) => this.availability.set(report),
      error: () => this.availabilityError.set(true),
    });
  }

  private loadRecentActivity(): void {
    this.loadingActivity.set(true);
    this.activityError.set(false);
    this.auditApi.list({ page: 0, size: 6, sort: 'occurredAt,desc' })
      .pipe(finalize(() => this.loadingActivity.set(false)))
      .subscribe({
        next: (response) => this.recentActivity.set(response.items),
        error: () => this.activityError.set(true),
      });
  }

  private loadOrganizationTotal(): void {
    this.loadingOrganizations.set(true);
    this.organizationsError.set(false);
    this.managementApi.listOrganizations({ page: 0, size: 1, sort: 'legalName,asc' })
      .pipe(finalize(() => this.loadingOrganizations.set(false)))
      .subscribe({
        next: (response) => this.organizationTotal.set(response.totalItems),
        error: () => this.organizationsError.set(true),
      });
  }
}
