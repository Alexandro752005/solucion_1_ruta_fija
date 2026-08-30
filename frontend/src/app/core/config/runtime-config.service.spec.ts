import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  RUNTIME_CONFIG_TIMEOUT_MS,
  RuntimeConfigService,
} from './runtime-config.service';

describe('RuntimeConfigService', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('acepta y normaliza únicamente una ruta API del mismo origen', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          apiBaseUrl: `${globalThis.location.origin}/api/v1/`,
          appName: 'Ruta Fija',
          environment: 'test',
        }),
      ),
    );
    const service = new RuntimeConfigService();

    await service.load();

    expect(service.apiBaseUrl).toBe('/api/v1');
    expect(service.isApiUrl('/api/v1/users')).toBe(true);
    expect(service.isApiUrl('/api/v10/users')).toBe(false);
    expect(service.isApiUrl('https://example.invalid/api/v1/users')).toBe(false);
  });

  it.each(['/', '', 'https://example.invalid/api/v1']) (
    'rechaza apiBaseUrl insegura: %s',
    async (apiBaseUrl) => {
      const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          jsonResponse({
            apiBaseUrl,
            appName: 'Ruta Fija',
            environment: 'test',
          }),
        ),
      );
      const service = new RuntimeConfigService();

      await service.load();

      expect(service.apiBaseUrl).toBe('/api/v1');
      expect(warning).toHaveBeenCalledOnce();
    },
  );

  it('aborta la carga y conserva valores seguros al superar el timeout', async () => {
    vi.useFakeTimers();
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () =>
            reject(new DOMException('Aborted', 'AbortError')),
          );
        }),
      ),
    );
    const service = new RuntimeConfigService();
    const loading = service.load();

    await vi.advanceTimersByTimeAsync(RUNTIME_CONFIG_TIMEOUT_MS);
    await loading;

    expect(service.apiBaseUrl).toBe('/api/v1');
    expect(warning).toHaveBeenCalledOnce();
  });

  function jsonResponse(body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  }
});
