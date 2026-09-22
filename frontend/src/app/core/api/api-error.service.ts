import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { ApiFieldError, ApiProblem, UiError } from './api-error.model';

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  toUiError(
    error: unknown,
    fallbackMessage = 'No fue posible completar la operación. Inténtalo nuevamente.',
  ): UiError {
    if (!(error instanceof HttpErrorResponse)) {
      return { message: fallbackMessage, fieldErrors: [] };
    }

    const problem = this.readProblem(error.error);
    if (problem !== null) {
      return {
        message: problem.message || fallbackMessage,
        code: problem.code,
        correlationId: problem.correlationId,
        fieldErrors: problem.errors ?? problem.details ?? [],
      };
    }

    if (error.status === 0) {
      return {
        message: 'No se pudo conectar con el servidor. Verifica que el backend local esté iniciado.',
        fieldErrors: [],
      };
    }

    return { message: fallbackMessage, fieldErrors: [] };
  }

  private readProblem(value: unknown): ApiProblem | null {
    if (typeof value !== 'object' || value === null) {
      return null;
    }

    const record = value as Record<string, unknown>;
    if (
      typeof record['status'] !== 'number' ||
      typeof record['code'] !== 'string' ||
      typeof record['message'] !== 'string'
    ) {
      return null;
    }

    return {
      status: record['status'],
      code: record['code'],
      message: record['message'],
      timestamp: this.optionalString(record['timestamp']),
      path: this.optionalString(record['path']),
      correlationId: this.optionalString(record['correlationId']),
      errors: this.readFieldErrors(record['errors']),
      details: this.readFieldErrors(record['details']),
    };
  }

  private readFieldErrors(value: unknown): readonly ApiFieldError[] | undefined {
    if (!Array.isArray(value)) {
      return undefined;
    }

    return value.flatMap((entry: unknown) => {
      if (typeof entry !== 'object' || entry === null) {
        return [];
      }

      const record = entry as Record<string, unknown>;
      return typeof record['field'] === 'string' &&
        typeof record['message'] === 'string'
        ? [{ field: record['field'], message: record['message'] }]
        : [];
    });
  }

  private optionalString(value: unknown): string | undefined {
    return typeof value === 'string' ? value : undefined;
  }
}
