import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  GroupPayload,
  ManagedUser,
  TransportGroup,
} from '../../core/management/management.models';

@Component({
  selector: 'rf-groups-page',
  imports: [ReactiveFormsModule],
  templateUrl: './groups.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupsPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);

  readonly canManage = computed(() =>
    this.session.hasAnyRole(['ADMINISTRADOR']),
  );
  readonly groups = signal<readonly TransportGroup[]>([]);
  readonly coordinators = signal<readonly ManagedUser[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly assigning = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly editing = signal<TransportGroup | null>(null);
  readonly selectedGroup = signal<TransportGroup | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

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
    this.api
      .listGroups({
        page,
        size: 20,
        sort: 'name,asc',
        search: this.search().trim() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
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

  applySearch(value: string): void {
    this.search.set(value);
    this.load(0);
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
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el grupo.')),
    });
  }

  startEdit(group: TransportGroup): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(group);
    this.form.reset({
      name: group.name,
      description: group.description ?? '',
      active: group.active,
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  manageCoordinators(group: TransportGroup): void {
    this.selectedGroup.set(group);
    this.coordinatorControl.reset('');
    this.notice.set(null);
    this.error.set(null);
  }

  assignCoordinator(): void {
    const group = this.selectedGroup();
    if (group === null || this.coordinatorControl.invalid) {
      this.coordinatorControl.markAsTouched();
      return;
    }

    this.assigning.set(true);
    this.error.set(null);
    this.api
      .assignCoordinator(group.id, this.coordinatorControl.getRawValue())
      .pipe(finalize(() => this.assigning.set(false)))
      .subscribe({
        next: (updated) => {
          this.selectedGroup.set(updated);
          this.coordinatorControl.reset('');
          this.notice.set('Coordinador asignado correctamente.');
          this.load();
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo asignar el coordinador.')),
      });
  }

  removeCoordinator(userId: string, fullName: string): void {
    const group = this.selectedGroup();
    if (group === null || !globalThis.confirm(`¿Retirar a ${fullName} de este grupo?`)) {
      return;
    }

    this.error.set(null);
    this.api.removeCoordinator(group.id, userId).subscribe({
      next: () => {
        this.selectedGroup.set({
          ...group,
          coordinators: group.coordinators.filter((coordinator) => coordinator.userId !== userId),
        });
        this.notice.set('Coordinador retirado correctamente.');
        this.load();
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo retirar el coordinador.')),
    });
  }

  assignableCoordinators(): readonly ManagedUser[] {
    const assigned = new Set(
      this.selectedGroup()?.coordinators.map((coordinator) => coordinator.userId) ?? [],
    );
    return this.coordinators().filter((coordinator) => !assigned.has(coordinator.id));
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

  private loadCoordinators(): void {
    this.api
      .listUsers({
        page: 0,
        size: 100,
        sort: 'fullName,asc',
        role: 'COORDINADOR',
        active: true,
      })
      .subscribe({
        next: (response) => this.coordinators.set(response.items),
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los coordinadores.')),
      });
  }

  private resetForm(): void {
    this.editing.set(null);
    this.form.reset({ name: '', description: '', active: true });
  }
}
