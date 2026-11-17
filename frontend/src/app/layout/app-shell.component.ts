import { ChangeDetectionStrategy, Component, DestroyRef, Injector, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, finalize } from 'rxjs';

import { AuthSessionService } from '../core/auth/auth-session.service';
import { CRM_ROLES, UserRole } from '../core/auth/auth.models';
import { RuntimeConfigService } from '../core/config/runtime-config.service';
import { CrmTopbarComponent } from './topbar/crm-topbar.component';
import { selectTopbarModel } from './topbar/topbar.model';
import { TOPBAR_ACTIVATION, usesNewTopbar } from './topbar-activation';
import { ShellSessionContext, crmSessionContextKey } from './shell-session-context';

interface NavigationItem {
  readonly label: string;
  readonly route: string;
  readonly roles: readonly UserRole[];
}

@Component({
  selector: 'rf-app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, CrmTopbarComponent],
  providers: [ShellSessionContext],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppShellComponent {
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly topbarRoles = inject(TOPBAR_ACTIVATION);
  private readonly destroyRef = inject(DestroyRef);

  readonly session = inject(AuthSessionService);
  readonly context = inject(ShellSessionContext);
  readonly runtimeConfig = inject(RuntimeConfigService);
  readonly loggingOut = signal(false);
  readonly logoutError = signal(false);
  readonly mobileNavigationOpen = signal(false);
  readonly topbarModel = computed(() => selectTopbarModel(this.session.user()?.role));
  readonly sessionContextKey = computed(() => crmSessionContextKey(this.session.user()) ?? '');
  readonly newTopbarEnabled = computed(() => usesNewTopbar(this.session.user()?.role, this.topbarRoles));
  readonly navigation: readonly NavigationItem[] = [
    { label: 'Resumen', route: '/dashboard', roles: CRM_ROLES },
    { label: 'Usuarios', route: '/users', roles: ['ADMIN'] },
    { label: 'Invitaciones', route: '/invitations', roles: ['ADMIN'] },
    { label: 'Solicitudes', route: '/onboarding-requests', roles: ['ADMIN'] },
    { label: 'Organización', route: '/organization', roles: ['ADMIN'] },
    { label: 'Grupos', route: '/groups', roles: ['ADMIN'] },
    { label: 'Conductores', route: '/drivers', roles: ['ADMIN'] },
    { label: 'Vehículos', route: '/vehicles', roles: ['ADMIN'] },
    { label: 'Asignaciones', route: '/assignments', roles: ['ADMIN'] },
    { label: 'Rutas', route: '/routes', roles: ['ADMIN'] },
    { label: 'Incidencias', route: '/incidents', roles: ['ADMIN'] },
    { label: 'Comunicados', route: '/announcements', roles: ['ADMIN'] },
    { label: 'Bandeja', route: '/notices', roles: ['ADMIN'] },
    { label: 'Reportes', route: '/reports', roles: ['ADMIN'] },
    { label: 'Auditoría', route: '/audit', roles: ['ADMIN'] },
    { label: 'Organizaciones', route: '/organizations', roles: ['SUPER_ADMIN'] },
    { label: 'Cuentas ADMIN', route: '/platform-admins', roles: ['SUPER_ADMIN'] },
    { label: 'Auditoría de plataforma', route: '/platform-audit', roles: ['SUPER_ADMIN'] },
  ];
  readonly visibleNavigation = computed(() => {
    const role = this.session.user()?.role;
    return role === undefined
      ? []
      : this.navigation.filter((item) => item.roles.includes(role));
  });
  readonly initials = () => 'RF';
  readonly contextLabel = computed(() => {
    const user = this.session.user();
    if (user?.role === 'SUPER_ADMIN') return 'Ámbito de plataforma';
    if (user?.organizationName?.trim()) {
      return user.organizationName.trim();
    }

    return 'Organización asignada';
  });

  constructor() {
    effect(() => {
      if (this.session.status() !== 'authenticated' || crmSessionContextKey(this.session.user()) !== null) return;
      queueMicrotask(() => {
        if (!this.destroyRef.destroyed && this.session.status() === 'authenticated'
          && crmSessionContextKey(this.session.user()) === null) void this.router.navigate(['/forbidden'], { replaceUrl: true });
      });
    });
    effect(() => {
      if (this.session.status() !== 'anonymous' || this.loggingOut()) return;
      // Existing logout/interceptor navigation keeps its reason. This fallback
      // handles an independently cleared session without retaining the old view.
      queueMicrotask(() => {
        if (this.destroyRef.destroyed || this.session.status() !== 'anonymous' || this.loggingOut()
          || this.router.url.startsWith('/login')) return;
        void this.router.navigate(['/login'], { replaceUrl: true, queryParams: {
          reason: this.logoutError() ? 'logout-unconfirmed' : 'session-expired',
        } });
      });
    });
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      takeUntilDestroyed(),
    ).subscribe(() => {
      if (!this.newTopbarEnabled()) return;
      afterNextRender(() => {
        if (!this.newTopbarEnabled() || !this.context.canRender() || document.querySelector('[role="dialog"][aria-modal="true"]')) return;
        document.getElementById('main-content')?.focus({ preventScroll: true });
      }, { injector: this.injector });
    });
  }

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
      case 'ADMIN':
        return 'Admin';
      case 'CONDUCTOR':
        return 'Conductor';
      default:
        return 'Sin rol';
    }
  }
}
