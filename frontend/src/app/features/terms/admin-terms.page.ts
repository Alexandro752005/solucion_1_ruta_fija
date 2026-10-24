import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AdminTermsApiService } from '../../core/terms/admin-terms-api.service';
import { AdminTermsDocument } from '../../core/terms/admin-terms.models';

@Component({
  selector: 'rf-admin-terms-page',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-terms.page.html',
  styleUrl: './admin-terms.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminTermsPage implements OnInit {
  private readonly api = inject(AdminTermsApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly terms = signal<AdminTermsDocument | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly noPublishedVersion = signal(false);
  readonly loadError = signal<UiError | null>(null);
  readonly confirmationError = signal<string | null>(null);
  readonly explicitAcceptance = new FormControl(false, {
    nonNullable: true,
    validators: [Validators.requiredTrue],
  });
  readonly acceptanceForm = new FormGroup({
    accepted: this.explicitAcceptance,
  });

  ngOnInit(): void {
    this.loadCurrentTerms();
  }

  loadCurrentTerms(): void {
    this.loading.set(true);
    this.noPublishedVersion.set(false);
    this.loadError.set(null);
    this.confirmationError.set(null);
    this.terms.set(null);
    this.explicitAcceptance.reset(false);

    this.api
      .getCurrentTerms()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (document) => this.terms.set(document),
        error: (error: unknown) => {
          if (
            (error instanceof HttpErrorResponse && error.status === 404) ||
            error instanceof Error && error.name === 'SyntheticTermsUnavailableError'
          ) {
            this.noPublishedVersion.set(true);
            return;
          }
          this.loadError.set(
            this.apiErrors.toUiError(
              error,
              'No se pudo consultar la versión aprobada de términos.',
            ),
          );
        },
      });
  }

  acceptCurrentTerms(): void {
    const document = this.terms();
    if (document === null || document.accepted || this.submitting()) {
      return;
    }
    this.confirmationError.set(null);
    if (this.explicitAcceptance.invalid) {
      this.explicitAcceptance.markAsTouched();
      return;
    }

    this.submitting.set(true);
    this.api
      .acceptCurrentTerms(document)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (acceptance) => {
          this.terms.set({
            ...document,
            accepted: true,
            acceptedAt: acceptance.acceptedAt,
          });
          this.explicitAcceptance.reset(false);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.confirmationError.set(
              'La versión vigente cambió. Consulta nuevamente el documento antes de aceptarlo.',
            );
            return;
          }
          this.loadError.set(
            this.apiErrors.toUiError(
              error,
              'No se pudo registrar la aceptación expresa.',
            ),
          );
        },
      });
  }

  continueToCrm(): void {
    void this.router.navigateByUrl(this.safeReturnUrl());
  }

  private safeReturnUrl(): string {
    const candidate = this.route.snapshot.queryParamMap.get('returnUrl');
    return candidate !== null &&
      candidate.startsWith('/') &&
      !candidate.startsWith('//')
      ? candidate
      : '/dashboard';
  }
}
