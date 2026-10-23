import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { GroupPayload, GroupResources, TransportGroup } from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

@Component({
  selector: 'rf-groups-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './groups.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupsPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  readonly session = inject(AuthSessionService);

  readonly canManage = computed(() => this.session.hasAnyRole(['ADMIN']));
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly groups = signal<readonly TransportGroup[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly activeFilter = signal<boolean | undefined>(undefined);
  readonly editing = signal<TransportGroup | null>(null);
  readonly formOpen = signal(false);
  readonly confirmingDeactivation = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  private deactivationApproved = false;
  readonly resourcesGroup = signal<TransportGroup | null>(null);
  readonly resources = signal<GroupResources | null>(null);
  readonly resourcesLoading = signal(false);
  readonly resourcesSaving = signal(false);
  readonly resourcesUncertain = signal(false);
  readonly resourceCandidates = signal<readonly { id: string; kind: 'DRIVER' | 'VEHICLE' }[]>([]);

  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(300)],
    }),
    active: new FormControl(true, { nonNullable: true }),
  });
  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listGroups({
      page,
      size: 20,
      sort: 'name,asc',
      search: this.search().trim() || undefined,
      active: this.activeFilter(),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.groups.set(response.items);
        this.page.set(response.page);
        this.totalPages.set(response.totalPages);
        this.totalItems.set(response.totalItems);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los grupos.')),
    });
  }

  applyFilters(search: string, active: string): void {
    this.search.set(search);
    this.activeFilter.set(active === '' ? undefined : active === 'true');
    this.load(0);
  }

  clearFilters(): void {
    this.search.set('');
    this.activeFilter.set(undefined);
    this.load(0);
  }

  startCreate(): void {
    this.notice.set(null);
    this.error.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
  }

  startEdit(group: TransportGroup): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(group);
    this.form.reset({ name: group.name, description: group.description ?? '', active: group.active });
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (!this.saving()) {
      this.resetForm();
    }
  }

  submit(): void {
    this.notice.set(null);
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const current = this.editing();
    const value = this.form.getRawValue();
    if (current?.active && !value.active && !this.deactivationApproved) {
      this.resourcesLoading.set(true);
      this.api.getGroupResources(current.id).pipe(finalize(() => this.resourcesLoading.set(false))).subscribe({
        next: (resources) => {
          if (resources.commitments.length) {
            this.resources.set(resources);
            this.resourcesGroup.set(current);
            this.error.set(this.apiErrors.toUiError(null, 'Resuelve los compromisos mostrados antes de retirar el grupo.'));
          } else {
            this.confirmingDeactivation.set(true);
          }
        },
        error: (error: unknown) => this.error.set(this.apiErrors.toUiError(error, 'No se pudieron comprobar los compromisos.')),
      });
      return;
    }

    this.deactivationApproved = false;
    this.saving.set(true);
    const request = current === null
      ? this.api.createGroup({
          name: value.name.trim(),
          description: value.description.trim() || undefined,
        } satisfies GroupPayload)
      : this.api.updateGroup(current.id, {
          name: value.name.trim(),
          description: value.description.trim() || undefined,
          active: value.active,
          expectedVersion: current.version ?? 0,
        });

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Grupo creado correctamente.' : 'Grupo actualizado correctamente.');
        this.resetForm();
        this.load(this.page());
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el grupo.')),
    });
  }

  confirmDeactivation(): void {
    this.confirmingDeactivation.set(false);
    this.deactivationApproved = true;
    this.submit();
  }

  cancelDeactivation(): void {
    this.confirmingDeactivation.set(false);
  }

  openResources(group: TransportGroup): void {
    this.resourcesGroup.set(group);
    this.resources.set(null);
    this.resourceCandidates.set([]);
    this.resourcesUncertain.set(false);
    this.reloadResources();
    forkJoin({ drivers: this.api.listDrivers({ size: 100 }), vehicles: this.api.listVehicles({ size: 100 }) }).subscribe({
      next: ({ drivers, vehicles }) => this.resourceCandidates.set([
        ...drivers.items.map((d) => ({ id: d.id, kind: 'DRIVER' as const })),
        ...vehicles.items.map((v) => ({ id: v.id, kind: 'VEHICLE' as const })),
      ]),
      error: (error: unknown) => this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los recursos de la organización.')),
    });
  }

  reloadResources(): void {
    const group = this.resourcesGroup();
    if (!group || this.resourcesSaving()) return;
    this.resourcesLoading.set(true);
    this.api.getGroupResources(group.id).pipe(finalize(() => this.resourcesLoading.set(false))).subscribe({
      next: (value) => { this.resources.set(value); this.resourcesUncertain.set(false); },
      error: (error: unknown) => { this.resourcesUncertain.set(true); this.error.set(this.apiErrors.toUiError(error, 'Consulta de recursos no confirmada.')); },
    });
  }

  changeMembership(kind: 'DRIVER' | 'VEHICLE', resourceId: string, active: boolean, reason: string): void {
    const group = this.resourcesGroup();
    const state = this.resources();
    if (!group || !state || !state.active || this.resourcesSaving() || this.resourcesLoading() || this.resourcesUncertain() || !reason.trim()) return;
    this.resourcesSaving.set(true);
    this.api.changeGroupMembership(group.id, { expectedVersion: state.version, operationKey: crypto.randomUUID(), reason: reason.trim(), resourceKind: kind, resourceId, active })
      .pipe(finalize(() => this.resourcesSaving.set(false))).subscribe({
        next: (value) => { this.resources.set(value); this.notice.set('Membresía confirmada. No concede permisos ni vínculo de unidad.'); },
        error: (error: unknown) => { this.resourcesUncertain.set(true); this.error.set(this.apiErrors.toUiError(error, 'Cambio no confirmado. Consulta el estado antes de otra acción.')); },
      });
  }

  addResource(selection: string, reason: string): void {
    const [kind, id] = selection.split(':');
    if ((kind === 'DRIVER' || kind === 'VEHICLE') && id) this.changeMembership(kind, id, true, reason);
  }

  closeResources(): void {
    if (this.resourcesSaving()) return;
    this.resourcesGroup.set(null);
    this.resources.set(null);
    this.resourceCandidates.set([]);
  }

  previousPage(): void { if (this.page() > 0) this.load(this.page() - 1); }
  commitmentStatusLabel(status:string):string { return ({PENDING_RESPONSE:'Pendiente de respuesta',SCHEDULED:'Programada',EN_SERVICIO:'En servicio',INTERRUPTED:'Interrumpida',TRANSFERRED:'Tramo relevado',COMPLETED:'Completada',CANCELLED:'Cancelada',EXPIRED:'Vencida',REJECTED:'Rechazada'} as Record<string,string>)[status]??'Compromiso registrado'; }
  attentionLabel(kind:string|null|undefined):string { return ({SAFETY_ATTENTION:'Atención segura pendiente de conductor o unidad',INTERRUPTED_CONTINUITY:'Continuidad interrumpida pendiente de decisión',PENDING_REASSIGNMENT:'Reasignación pendiente'} as Record<string,string>)[kind??'']??'Compromiso vigente'; }
  nextPage(): void { if (this.page() + 1 < this.totalPages()) this.load(this.page() + 1); }

  private resetForm(close = true): void {
    this.editing.set(null);
    this.deactivationApproved = false;
    this.confirmingDeactivation.set(false);
    this.form.reset({ name: '', description: '', active: true });
    if (close) this.formOpen.set(false);
  }
}
