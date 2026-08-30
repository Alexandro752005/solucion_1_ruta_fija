import { Injectable, signal } from '@angular/core';

import { RuntimeConfig } from './runtime-config.model';

const DEFAULT_CONFIG: RuntimeConfig = Object.freeze({
  apiBaseUrl: '/api/v1',
  appName: 'Ruta Fija',
  environment: 'local',
});

export const RUNTIME_CONFIG_TIMEOUT_MS = 5_000;

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {
  private readonly configState = signal<RuntimeConfig>(DEFAULT_CONFIG);

  readonly config = this.configState.asReadonly();

  get apiBaseUrl(): string {
    return this.configState().apiBaseUrl;
  }

  async load(): Promise<void> {
    const controller = new AbortController();
    const timeoutId = globalThis.setTimeout(
      () => controller.abort(),
      RUNTIME_CONFIG_TIMEOUT_MS,
    );

    try {
      const response = await fetch('/config/runtime-config.json', {
        cache: 'no-store',
        credentials: 'same-origin',
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const candidate: unknown = await response.json();
      if (!this.isRuntimeConfig(candidate)) {
        throw new Error('El archivo no cumple el esquema esperado.');
      }

      this.configState.set(
        Object.freeze({
          apiBaseUrl: this.normalizeBaseUrl(candidate.apiBaseUrl),
          appName: candidate.appName.trim(),
          environment: candidate.environment.trim(),
        }),
      );
    } catch (error: unknown) {
      console.warn(
        'No se pudo cargar la configuración de ejecución; se usarán valores locales seguros.',
        error instanceof Error ? error.message : 'Error desconocido',
      );
    } finally {
      globalThis.clearTimeout(timeoutId);
    }
  }

  apiUrl(path: string): string {
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    return `${this.apiBaseUrl}${normalizedPath}`;
  }

  isApiUrl(url: string): boolean {
    try {
      const origin = globalThis.location.origin;
      const base = new URL(this.apiBaseUrl, `${origin}/`);
      const target = new URL(url, `${origin}/`);
      return (
        target.origin === base.origin &&
        (target.pathname === base.pathname ||
          target.pathname.startsWith(`${base.pathname}/`))
      );
    } catch {
      return false;
    }
  }

  private normalizeBaseUrl(value: string): string {
    const trimmed = value.trim();
    const origin = globalThis.location.origin;
    const parsed = new URL(trimmed, `${origin}/`);

    if (
      parsed.origin !== origin ||
      parsed.username.length > 0 ||
      parsed.password.length > 0 ||
      parsed.search.length > 0 ||
      parsed.hash.length > 0
    ) {
      throw new Error('apiBaseUrl debe ser una ruta limpia del mismo origen.');
    }

    const normalizedPath = parsed.pathname.replace(/\/+$/, '');
    if (normalizedPath.length === 0 || normalizedPath === '/') {
      throw new Error('apiBaseUrl no puede apuntar a la raíz del sitio.');
    }

    return normalizedPath;
  }

  private isRuntimeConfig(value: unknown): value is RuntimeConfig {
    if (typeof value !== 'object' || value === null) {
      return false;
    }

    const record = value as Record<string, unknown>;
    return (
      typeof record['apiBaseUrl'] === 'string' &&
      record['apiBaseUrl'].trim().length > 0 &&
      typeof record['appName'] === 'string' &&
      record['appName'].trim().length > 0 &&
      typeof record['environment'] === 'string' &&
      record['environment'].trim().length > 0
    );
  }
}
