import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../core/auth/auth-session.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import { AvailabilityReport } from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';

@Component({
  selector: 'rf-dashboard-page',
  imports: [RouterLink],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  readonly session = inject(AuthSessionService);
  private readonly api = inject(OperationsApiService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly canManageResources = computed(() =>
    this.session.hasAnyRole(['ADMINISTRADOR']),
  );
  readonly canBrowseResources = computed(() =>
    this.session.hasAnyRole(['ADMINISTRADOR', 'COORDINADOR']),
  );
  readonly canViewReports = computed(() =>
    this.session.hasAnyRole(['ADMINISTRADOR']),
  );
  readonly isSuperAdmin = computed(() =>
    this.session.hasAnyRole(['SUPER_ADMIN']),
  );
  readonly organizationContext = computed(() => {
    const user = this.session.user();
    if (user?.organizationName?.trim()) {
      return user.organizationName.trim();
    }

    if (user?.organizationId) {
      return 'Organización asignada';
    }

    return user?.role === 'SUPER_ADMIN'
      ? 'Ámbito de plataforma'
      : 'Contexto protegido';
  });

  readonly availability = signal<AvailabilityReport | null>(null);
  readonly loadingAvailability = signal(false);
  readonly availabilityError = signal(false);

  constructor() {
    if (this.session.hasAnyRole(['ADMINISTRADOR'])) {
      this.loadAvailability();
      this.realtime.connect();
      this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadAvailability());
      this.destroyRef.onDestroy(() => this.realtime.disconnect());
    }
  }

  countByStatus(items: readonly { readonly status: string; readonly total: number }[], status: string): number {
    return items.find((item) => item.status === status)?.total ?? 0;
  }

  private loadAvailability(): void {
    this.loadingAvailability.set(true);
    this.availabilityError.set(false);
    this.api.availabilityReport().pipe(finalize(() => this.loadingAvailability.set(false))).subscribe({
      next: (report) => this.availability.set(report),
      error: () => this.availabilityError.set(true),
    });
  }
}
