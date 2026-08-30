import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  ManagedUser,
  UserCreatePayload,
  UserUpdatePayload,
} from '../../core/management/management.models';

type OrganizationUserRole = 'ADMINISTRADOR' | 'COORDINADOR' | 'CONDUCTOR';

@Component({
  selector: 'rf-users-page',
  imports: [ReactiveFormsModule],
  templateUrl: './users.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly roles: readonly OrganizationUserRole[] = [
    'ADMINISTRADOR',
    'COORDINADOR',
    'CONDUCTOR',
  ];
  readonly users = signal<readonly ManagedUser[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly editing = signal<ManagedUser | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(180)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12), Validators.maxLength(128)],
    }),
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
    role: new FormControl<OrganizationUserRole>('COORDINADOR', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listUsers({
        page,
        size: 20,
        sort: 'fullName,asc',
        search: this.search().trim() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.users.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los usuarios.')),
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
      ? this.api.createUser({
          email: value.email.trim(),
          password: value.password,
          fullName: value.fullName.trim(),
          phone: value.phone.trim() || undefined,
          role: value.role,
        } satisfies UserCreatePayload)
      : this.api.updateUser(current.id, {
          email: value.email.trim(),
          fullName: value.fullName.trim(),
          phone: value.phone.trim() || undefined,
          role: value.role,
        } satisfies UserUpdatePayload);

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Usuario creado correctamente.' : 'Usuario actualizado correctamente.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el usuario.')),
    });
  }

  startEdit(user: ManagedUser): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(user);
    this.form.patchValue({
      email: user.email,
      fullName: user.fullName,
      phone: user.phone ?? '',
      role: user.role as OrganizationUserRole,
    });
    this.form.controls.password.disable();
  }

  cancelEdit(): void {
    this.resetForm();
  }

  toggleActive(user: ManagedUser): void {
    const action = user.active ? 'desactivar' : 'activar';
    if (!globalThis.confirm(`¿Deseas ${action} a ${user.fullName}?`)) {
      return;
    }

    this.notice.set(null);
    this.error.set(null);
    this.api.setUserActive(user.id, !user.active).subscribe({
      next: () => {
        this.notice.set(`Usuario ${user.active ? 'desactivado' : 'activado'} correctamente.`);
        this.load();
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo actualizar el estado del usuario.')),
    });
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

  roleLabel(role: string): string {
    return ({
      ADMINISTRADOR: 'Administrador',
      COORDINADOR: 'Coordinador',
      CONDUCTOR: 'Conductor',
    } as Record<string, string>)[role] ?? role;
  }

  private resetForm(): void {
    this.editing.set(null);
    this.form.controls.password.enable();
    this.form.reset({
      email: '',
      password: '',
      fullName: '',
      phone: '',
      role: 'COORDINADOR',
    });
  }
}
