import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { ApiErrorService } from './api-error.service';

describe('ApiErrorService', () => {
  const service = new ApiErrorService();

  it('normaliza el error documentado y conserva la referencia', () => {
    const result = service.toUiError(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          code: 'VALIDATION_ERROR',
          message: 'Datos inválidos',
          correlationId: 'corr-123',
          errors: [{ field: 'email', message: 'Correo requerido' }],
        },
      }),
    );

    expect(result).toEqual({
      message: 'Datos inválidos',
      code: 'VALIDATION_ERROR',
      correlationId: 'corr-123',
      fieldErrors: [{ field: 'email', message: 'Correo requerido' }],
    });
  });

  it('explica un fallo de conexión sin exponer datos internos', () => {
    const result = service.toUiError(new HttpErrorResponse({ status: 0 }));

    expect(result.message).toContain('No se pudo conectar');
    expect(result.fieldErrors).toEqual([]);
  });
});
