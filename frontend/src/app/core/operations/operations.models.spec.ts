import { describe, expect, it } from 'vitest';

import { ASSIGNMENT_RESPONSE_MODES, ASSIGNMENT_STATUSES } from './operations.models';

describe('contrato de asignaciones CRM-móvil', () => {
  it('reconoce todos los estados persistidos de respuesta móvil', () => {
    expect(ASSIGNMENT_STATUSES).toEqual([
      'PENDING_RESPONSE',
      'SCHEDULED',
      'EN_SERVICIO',
      'COMPLETED',
      'REJECTED',
      'CANCELLED',
      'EXPIRED',
    ]);
  });

  it('distingue programación directa de confirmación móvil', () => {
    expect(ASSIGNMENT_RESPONSE_MODES).toEqual(['ADMIN_DIRECT', 'MOBILE_CONFIRMATION']);
  });
});
