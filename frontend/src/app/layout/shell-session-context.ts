import { Injectable, InjectionToken, computed, effect, inject, signal, untracked } from '@angular/core';
import { AuthSessionService } from '../core/auth/auth-session.service';
import { crmSessionContextKey } from '../core/auth/auth-session-context';
export { crmSessionContextKey } from '../core/auth/auth-session-context';

/** Full document navigation discards callbacks and views owned by the previous actor. */
export const SHELL_CONTEXT_RELOAD = new InjectionToken<(path: '/dashboard') => void>('CRM context reload', {
  providedIn: 'root', factory: () => (path) => globalThis.location.replace(path),
});

/** Scoped to one shell instance; a new document starts with its newly confirmed actor. */
@Injectable()
export class ShellSessionContext {
  private readonly session = inject(AuthSessionService);
  private readonly reload = inject(SHELL_CONTEXT_RELOAD);
  private readonly mountedKey = signal<string | null>(
    this.session.status() === 'authenticated' ? crmSessionContextKey(this.session.user()) : null,
  );
  readonly reloading = signal(false);
  readonly canRender = computed(() => {
    const key = this.mountedKey();
    return !this.reloading() && key !== null && this.session.status() !== 'anonymous'
      && crmSessionContextKey(this.session.user()) === key;
  });

  constructor() {
    effect(() => {
      if (this.session.status() !== 'authenticated' || this.reloading()) return;
      const current = crmSessionContextKey(this.session.user());
      if (!current) return;
      const mounted = this.mountedKey();
      if (mounted === null) { this.mountedKey.set(current); return; }
      if (mounted === current) return;
      // canRender already rejects the changed key synchronously. Removing its
      // outlet discards the old view before a late callback can paint it again.
      this.reloading.set(true);
      untracked(() => this.reload('/dashboard'));
    });
  }
}
