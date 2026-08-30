export interface ApiFieldError {
  readonly field: string;
  readonly message: string;
}

export interface ApiProblem {
  readonly timestamp?: string;
  readonly status: number;
  readonly code: string;
  readonly message: string;
  readonly path?: string;
  readonly correlationId?: string;
  readonly errors?: readonly ApiFieldError[];
  readonly details?: readonly ApiFieldError[];
}

export interface UiError {
  readonly message: string;
  readonly code?: string;
  readonly correlationId?: string;
  readonly fieldErrors: readonly ApiFieldError[];
}
