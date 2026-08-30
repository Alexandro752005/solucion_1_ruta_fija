import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../core/auth/auth-session.service';
import { UserRole } from '../core/auth/auth.models';
import { RuntimeConfigService } from '../core/config/runtime-config.service';

@Component({
  selector: 'rf-app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  private readonly router = inject(Router);

  readonly session = inject(AuthSessionService);
  readonly runtimeConfig = inject(RuntimeConfigService);
  readonly loggingOut = signal(false);
  readonly logoutError = signal(false);
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
  readonly initials = computed(() => {
    const parts = this.session.user()?.fullName.trim().split(/\s+/) ?? [];
    return parts
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || 'RF';
  });
  readonly organizationLabel = computed(() => {
    const user = this.session.user();
    if (user?.organizationName?.trim()) {
      return user.organizationName.trim();
    }

    return user?.role === 'SUPER_ADMIN'
      ? 'Ámbito de plataforma'
      : 'Organización asignada';
  });

  logout(): void {
    if (this.loggingOut()) {
      return;
    }

    this.loggingOut.set(true);
    this.logoutError.set(false);
    this.session
      .logout()
      .pipe(finalize(() => this.loggingOut.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/login']),
        error: () => {
          this.logoutError.set(true);
          void this.router.navigate(['/login'], {
            queryParams: { reason: 'logout-unconfirmed' },
          });
        },
      });
  }

  roleLabel(role: UserRole | undefined): string {
    switch (role) {
      case 'SUPER_ADMIN':
        return 'Superadministrador';
      case 'ADMINISTRADOR':
        return 'Administrador';
      case 'COORDINADOR':
        return 'Coordinador';
      case 'CONDUCTOR':
        return 'Conductor';
      default:
        return 'Sin rol';
    }
  }
}
