import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../core/auth/auth-session.service';
import { CRM_ROLES, UserRole } from '../core/auth/auth.models';
import { RuntimeConfigService } from '../core/config/runtime-config.service';

interface NavigationItem {
  readonly label: string;
  readonly route: string;
  readonly roles: readonly UserRole[];
}

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
  readonly mobileNavigationOpen = signal(false);
  readonly navigation: readonly NavigationItem[] = [
    { label: 'Resumen', route: '/dashboard', roles: CRM_ROLES },
    { label: 'Usuarios', route: '/users', roles: ['ADMINISTRADOR'] },
    { label: 'Organización', route: '/organization', roles: ['ADMINISTRADOR'] },
    { label: 'Grupos', route: '/groups', roles: ['ADMINISTRADOR', 'COORDINADOR'] },
    { label: 'Conductores', route: '/drivers', roles: ['ADMINISTRADOR', 'COORDINADOR'] },
    { label: 'Vehículos', route: '/vehicles', roles: ['ADMINISTRADOR', 'COORDINADOR'] },
    { label: 'Asignaciones', route: '/assignments', roles: ['ADMINISTRADOR', 'COORDINADOR'] },
    { label: 'Incidencias', route: '/incidents', roles: ['ADMINISTRADOR', 'COORDINADOR'] },
    { label: 'Comunicados', route: '/announcements', roles: ['ADMINISTRADOR', 'COORDINADOR'] },
    { label: 'Reportes', route: '/reports', roles: ['ADMINISTRADOR'] },
    { label: 'Auditoría', route: '/audit', roles: ['ADMINISTRADOR'] },
    { label: 'Organizaciones', route: '/organizations', roles: ['SUPER_ADMIN'] },
  ];
  readonly visibleNavigation = computed(() => {
    const role = this.session.user()?.role;
    return role === undefined
      ? []
      : this.navigation.filter((item) => item.roles.includes(role));
  });
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

  toggleMobileNavigation(): void {
    this.mobileNavigationOpen.update((open) => !open);
  }

  closeMobileNavigation(): void {
    this.mobileNavigationOpen.set(false);
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
