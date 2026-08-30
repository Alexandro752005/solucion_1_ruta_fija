import { afterEach, describe, expect, it, vi } from 'vitest';

import { CrossTabLockService } from './cross-tab-lock.service';

describe('CrossTabLockService', () => {
  const service = new CrossTabLockService();

  afterEach(() => {
    Reflect.deleteProperty(globalThis.navigator, 'locks');
    for (let index = globalThis.localStorage.length - 1; index >= 0; index -= 1) {
      const key = globalThis.localStorage.key(index);
      if (key?.startsWith('rf:cross-tab-lock:')) {
        globalThis.localStorage.removeItem(key);
      }
    }
    vi.restoreAllMocks();
  });

  it('utiliza Web Locks como mecanismo principal', async () => {
    const request = vi.fn(
      async (
        _name: string,
        _options: LockOptions,
        callback: LockGrantedCallback<unknown>,
      ): Promise<unknown> =>
        await callback({ name: 'ruta-fija-auth-refresh', mode: 'exclusive' }),
    );
    Object.defineProperty(globalThis.navigator, 'locks', {
      configurable: true,
      value: { request } as unknown as LockManager,
    });

    const result = await service.runExclusive(
      'ruta-fija-auth-refresh',
      async () => 'rotated',
    );

    expect(result).toBe('rotated');
    expect(request).toHaveBeenCalledOnce();
    expect(request.mock.calls[0]?.[0]).toBe('ruta-fija-auth-refresh');
    expect(request.mock.calls[0]?.[1]).toEqual({ mode: 'exclusive' });
  });

  it('usa un lease efímero sin persistir credenciales cuando Web Locks no existe', async () => {
    Object.defineProperty(globalThis.navigator, 'locks', {
      configurable: true,
      value: undefined,
    });
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');

    const result = await service.runExclusive(
      'ruta-fija-auth-refresh',
      async () => 'completed',
    );

    expect(result).toBe('completed');
    expect(storageSpy).toHaveBeenCalled();
    expect(
      storageSpy.mock.calls.every(
        ([key, value]) =>
          !key.toLowerCase().includes('token') &&
          !value.toLowerCase().includes('token'),
      ),
    ).toBe(true);
    expect(
      Array.from({ length: globalThis.localStorage.length }, (_, index) =>
        globalThis.localStorage.key(index),
      ).some((key) => key?.startsWith('rf:cross-tab-lock:')),
    ).toBe(false);
  });
});
