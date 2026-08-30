import { Injectable } from '@angular/core';

interface StorageLease {
  readonly owner: string;
  readonly expiresAt: number;
}

const LEASE_TTL_MS = 15_000;
const LEASE_WAIT_TIMEOUT_MS = 20_000;
const LEASE_SETTLE_MS = 25;
const LEASE_RETRY_MS = 75;

/**
 * Serializa operaciones sensibles entre pestañas del mismo origen.
 * Web Locks es la vía principal. El fallback solo persiste un identificador
 * efímero y una expiración; nunca escribe tokens ni datos de sesión.
 */
@Injectable({ providedIn: 'root' })
export class CrossTabLockService {
  async runExclusive<T>(name: string, operation: () => Promise<T>): Promise<T> {
    const lockManager = globalThis.navigator?.locks;
    if (lockManager !== undefined) {
      return lockManager.request(
        name,
        { mode: 'exclusive' },
        async () => operation(),
      );
    }

    const storage = this.availableStorage();
    if (storage === null) {
      // La coordinación dentro de la pestaña sigue cubierta por shareReplay.
      // Se evita persistir cualquier credencial como mecanismo alternativo.
      return operation();
    }

    return this.runWithStorageLease(storage, name, operation);
  }

  private async runWithStorageLease<T>(
    storage: Storage,
    name: string,
    operation: () => Promise<T>,
  ): Promise<T> {
    const key = `rf:cross-tab-lock:${name}`;
    const owner = globalThis.crypto.randomUUID();
    const waitDeadline = Date.now() + LEASE_WAIT_TIMEOUT_MS;

    while (Date.now() < waitDeadline) {
      if (await this.tryAcquire(storage, key, owner)) {
        const heartbeat = globalThis.setInterval(
          () => this.renew(storage, key, owner),
          LEASE_TTL_MS / 3,
        );

        try {
          return await operation();
        } finally {
          globalThis.clearInterval(heartbeat);
          this.release(storage, key, owner);
        }
      }

      await this.delay(LEASE_RETRY_MS);
    }

    throw new Error('No se pudo coordinar la renovación de sesión entre pestañas.');
  }

  private async tryAcquire(
    storage: Storage,
    key: string,
    owner: string,
  ): Promise<boolean> {
    const now = Date.now();
    const current = this.readLease(storage, key);
    if (current !== null && current.expiresAt > now && current.owner !== owner) {
      return false;
    }

    this.writeLease(storage, key, { owner, expiresAt: now + LEASE_TTL_MS });
    // Permite que escrituras competidoras se estabilicen antes de confirmar dueño.
    await this.delay(LEASE_SETTLE_MS);
    return this.readLease(storage, key)?.owner === owner;
  }

  private renew(storage: Storage, key: string, owner: string): void {
    if (this.readLease(storage, key)?.owner !== owner) {
      return;
    }

    this.writeLease(storage, key, {
      owner,
      expiresAt: Date.now() + LEASE_TTL_MS,
    });
  }

  private release(storage: Storage, key: string, owner: string): void {
    if (this.readLease(storage, key)?.owner === owner) {
      storage.removeItem(key);
    }
  }

  private readLease(storage: Storage, key: string): StorageLease | null {
    try {
      const raw = storage.getItem(key);
      if (raw === null) {
        return null;
      }

      const value: unknown = JSON.parse(raw);
      if (typeof value !== 'object' || value === null) {
        return null;
      }

      const record = value as Record<string, unknown>;
      return typeof record['owner'] === 'string' &&
        typeof record['expiresAt'] === 'number'
        ? { owner: record['owner'], expiresAt: record['expiresAt'] }
        : null;
    } catch {
      return null;
    }
  }

  private writeLease(storage: Storage, key: string, lease: StorageLease): void {
    storage.setItem(key, JSON.stringify(lease));
  }

  private availableStorage(): Storage | null {
    try {
      const storage = globalThis.localStorage;
      const probeKey = 'rf:cross-tab-lock:probe';
      storage.setItem(probeKey, '1');
      storage.removeItem(probeKey);
      return storage;
    } catch {
      return null;
    }
  }

  private delay(milliseconds: number): Promise<void> {
    return new Promise((resolve) => globalThis.setTimeout(resolve, milliseconds));
  }
}
