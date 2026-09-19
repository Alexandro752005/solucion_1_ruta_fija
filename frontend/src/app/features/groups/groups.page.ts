import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { GroupPayload, ManagedUser, TransportGroup } from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

interface PendingCoordinatorRemoval {
  readonly userId: string;
  readonly fullName: string;
}

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

  readonly canManage = computed(() => this.session.hasAnyRole(['ADMINISTRADOR']));
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly groups = signal<readonly TransportGroup[]>([]);
  readonly coordinators = signal<readonly ManagedUser[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly assigning = signal(false);
  readonly removing = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly activeFilter = signal<boolean | undefined>(undefined);
  readonly editing = signal<TransportGroup | null>(null);
  readonly formOpen = signal(false);
  readonly selectedGroup = signal<TransportGroup | null>(null);
  readonly coordinatorsOpen = signal(false);
  readonly pendingRemoval = signal<PendingCoordinatorRemoval | null>(null);
  readonly confirmingDeactivation = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  private deactivationApproved = false;

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
  readonly coordinatorControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  constructor() {
    this.load();
    if (this.canManage()) {
      this.loadCoordinators();
    }
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
      this.confirmingDeactivation.set(true);
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

  manageCoordinators(group: TransportGroup): void {
    this.selectedGroup.set(group);
    this.coordinatorControl.reset('');
    this.notice.set(null);
    this.error.set(null);
    this.coordinatorsOpen.set(true);
  }

  closeCoordinators(): void {
    if (!this.assigning() && !this.removing()) {
      this.coordinatorsOpen.set(false);
      this.pendingRemoval.set(null);
    }
  }

  assignCoordinator(): void {
    const group = this.selectedGroup();
    if (group === null || this.coordinatorControl.invalid) {
      this.coordinatorControl.markAsTouched();
      return;
    }

    this.assigning.set(true);
    this.error.set(null);
    this.api.assignCoordinator(group.id, this.coordinatorControl.getRawValue())
      .pipe(finalize(() => this.assigning.set(false)))
      .subscribe({
        next: (updated) => {
          this.selectedGroup.set(updated);
          this.coordinatorControl.reset('');
          this.notice.set('Coordinador asignado correctamente.');
          this.load(this.page());
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo asignar el coordinador.')),
      });
  }

  requestRemoveCoordinator(userId: string, fullName: string): void {
    this.pendingRemoval.set({ userId, fullName });
  }

  cancelRemoveCoordinator(): void {
    if (!this.removing()) {
      this.pendingRemoval.set(null);
    }
  }

  confirmRemoveCoordinator(): void {
    const group = this.selectedGroup();
    const removal = this.pendingRemoval();
    if (group === null || removal === null) {
      return;
    }

    this.removing.set(true);
    this.error.set(null);
    this.api.removeCoordinator(group.id, removal.userId).pipe(finalize(() => this.removing.set(false))).subscribe({
      next: () => {
        this.selectedGroup.set({
          ...group,
          coordinators: group.coordinators.filter((coordinator) => coordinator.userId !== removal.userId),
        });
        this.pendingRemoval.set(null);
        this.notice.set('Coordinador retirado correctamente.');
        this.load(this.page());
      },
      error: (error: unknown) => {
        this.pendingRemoval.set(null);
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo retirar el coordinador.'));
      },
    });
  }

  assignableCoordinators(): readonly ManagedUser[] {
    const assigned = new Set(this.selectedGroup()?.coordinators.map((coordinator) => coordinator.userId) ?? []);
    return this.coordinators().filter((coordinator) => !assigned.has(coordinator.id));
  }

  previousPage(): void { if (this.page() > 0) this.load(this.page() - 1); }
  nextPage(): void { if (this.page() + 1 < this.totalPages()) this.load(this.page() + 1); }

  private loadCoordinators(): void {
    this.api.listUsers({ page: 0, size: 100, sort: 'fullName,asc', role: 'COORDINADOR', active: true }).subscribe({
      next: (response) => this.coordinators.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los coordinadores.')),
    });
  }

  private resetForm(close = true): void {
    this.editing.set(null);
    this.deactivationApproved = false;
    this.confirmingDeactivation.set(false);
    this.form.reset({ name: '', description: '', active: true });
    if (close) this.formOpen.set(false);
  }
}
