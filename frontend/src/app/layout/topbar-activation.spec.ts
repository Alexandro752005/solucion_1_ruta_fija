import { describe, expect, it } from 'vitest';
import { usesNewTopbar } from './topbar-activation';

describe('política de activación TB-S6', () => {
  it('habilita ambos roles CRM conservando la exclusión de CONDUCTOR y sesión desconocida', () => {
    expect(usesNewTopbar('ADMIN')).toBe(true);
    expect(usesNewTopbar('SUPER_ADMIN')).toBe(true);
    expect(usesNewTopbar('CONDUCTOR')).toBe(false);
    expect(usesNewTopbar(undefined)).toBe(false);
  });
});
