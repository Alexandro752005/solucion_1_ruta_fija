import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { TransportGroup } from '../../core/management/management.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import {
  ANNOUNCEMENT_AUDIENCES,
  Announcement,
  AnnouncementAudience,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';

@Component({
  selector: 'rf-announcements-page',
  imports: [ReactiveFormsModule],
  templateUrl: './announcements.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementsPage {
  private readonly api = inject(OperationsApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly audiences = ANNOUNCEMENT_AUDIENCES;
  readonly canPublishOrganization = computed(() =>
    this.session.hasAnyRole(['ADMINISTRADOR']),
  );
  readonly announcements = signal<readonly Announcement[]>([]);
  readonly groups = signal<readonly TransportGroup[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedAudience = signal<AnnouncementAudience | ''>('');
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    body: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(5000)],
    }),
    audienceType: new FormControl<AnnouncementAudience>('ORGANIZATION', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    audienceId: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    if (!this.canPublishOrganization()) {
      this.form.controls.audienceType.setValue('GROUP');
    }
    this.load();
    this.loadGroups();
    this.realtime.connect();
    this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    this.destroyRef.onDestroy(() => this.realtime.disconnect());
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listAnnouncements({
        page,
        size: 20,
        sort: 'createdAt,desc',
        audienceType: this.selectedAudience() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.announcements.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los comunicados.')),
      });
  }

  applyAudience(value: string): void {
    this.selectedAudience.set(this.isAudience(value) ? value : '');
    this.load(0);
  }

  changeFormAudience(value: string): void {
    if (!this.isAudience(value)) {
      return;
    }
    if (value === 'ORGANIZATION' && !this.canPublishOrganization()) {
      this.form.controls.audienceType.setValue('GROUP');
      return;
    }
    this.form.controls.audienceType.setValue(value);
    this.form.controls.audienceId.setValue('');
  }

  submit(): void {
    this.error.set(null);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.audienceType === 'GROUP' && value.audienceId.length === 0) {
      this.error.set({ message: 'Selecciona el grupo destinatario.', fieldErrors: [] });
      return;
    }
    this.saving.set(true);
    this.api
      .createAnnouncement({
        title: value.title.trim(),
        body: value.body.trim(),
        audienceType: value.audienceType,
        audienceId: value.audienceType === 'GROUP' ? value.audienceId : undefined,
        requireReadAck: false,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notice.set('Comunicado publicado. No requiere acuse porque esta fase no tiene aplicación móvil.');
          this.form.reset({
            title: '',
            body: '',
            audienceType: this.canPublishOrganization() ? 'ORGANIZATION' : 'GROUP',
            audienceId: '',
          });
          this.load(0);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo publicar el comunicado.')),
      });
  }

  audienceLabel(audience: AnnouncementAudience): string {
    return audience === 'ORGANIZATION' ? 'Toda la organización' : 'Grupo específico';
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
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

  private loadGroups(): void {
    this.managementApi.listGroups({ page: 0, size: 100, sort: 'name,asc', active: true }).subscribe({
      next: (response) => this.groups.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los grupos.')),
    });
  }

  private isAudience(value: string): value is AnnouncementAudience {
    return (ANNOUNCEMENT_AUDIENCES as readonly string[]).includes(value);
  }
}
