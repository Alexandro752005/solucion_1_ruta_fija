import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { InvitationsApiService } from '../../core/invitations/invitations-api.service';
import { CreatedInvitation, InvitationSummary } from '../../core/invitations/invitations.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

const PAGE_SIZE = 20;

@Component({
  selector: 'rf-invitations-page',
  imports: [ReactiveFormsModule, DatePipe, PageHeaderComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './invitations.page.html',
  styleUrl: './invitations.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvitationsPage {
  private readonly api = inject(InvitationsApiService);
  private readonly apiErrors = inject(ApiErrorService);
  readonly session = inject(AuthSessionService);

  readonly invitations = signal<readonly InvitationSummary[]>([]);
  readonly issuedInvitation = signal<CreatedInvitation | null>(null);
  readonly loading = signal(true);
  readonly issuing = signal(false);
  readonly revoking = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly listError = signal<UiError | null>(null);
  readonly issueError = signal<UiError | null>(null);
  readonly issueMayHaveSucceeded = signal(false);
  readonly revokeError = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly pendingRevoke = signal<InvitationSummary | null>(null);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organización asignada',
  );

  readonly form = new FormGroup({
    recipientEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(180)],
    }),
  });

  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.listError.set(null);
    this.api.list(page, PAGE_SIZE).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.invitations.set(response.items);
        this.page.set(response.page);
        this.totalPages.set(response.totalPages);
        this.totalItems.set(response.totalItems);
      },
      error: (error: unknown) =>
        this.listError.set(this.apiErrors.toUiError(error, 'No se pudieron cargar las invitaciones.')),
    });
  }

  issue(): void {
    this.issueError.set(null);
    this.issueMayHaveSucceeded.set(false);
    this.revokeError.set(null);
    this.notice.set(null);
    this.issuedInvitation.set(null);
    if (this.form.invalid || this.issuing()) {
      this.form.markAllAsTouched();
      return;
    }

    this.issuing.set(true);
    this.api.issue({ recipientEmail: this.form.controls.recipientEmail.value.trim() })
      .pipe(finalize(() => this.issuing.set(false)))
      .subscribe({
        next: (issued) => {
          this.issuedInvitation.set(issued);
          this.form.reset({ recipientEmail: '' });
          this.notice.set('Invitación emitida. El código se muestra una sola vez en esta pantalla.');
          this.load(0);
        },
        error: (error: unknown) => {
          this.issueError.set(this.apiErrors.toUiError(error, 'No se pudo confirmar la emisión.'));
          this.issueMayHaveSucceeded.set(
            error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500),
          );
        },
      });
  }

  dismissCode(): void {
    this.issuedInvitation.set(null);
  }

  requestRevoke(invitation: InvitationSummary): void {
    this.notice.set(null);
    this.revokeError.set(null);
    this.issuedInvitation.set(null);
    this.pendingRevoke.set(invitation);
  }

  cancelRevoke(): void {
    if (!this.revoking()) {
      this.pendingRevoke.set(null);
    }
  }

  confirmRevoke(): void {
    const invitation = this.pendingRevoke();
    if (invitation === null || this.revoking()) {
      return;
    }

    this.revoking.set(true);
    this.api.revoke(invitation.id).pipe(finalize(() => this.revoking.set(false))).subscribe({
      next: () => {
        this.pendingRevoke.set(null);
        this.notice.set('Invitación revocada.');
        this.load();
      },
      error: (error: unknown) => {
        this.pendingRevoke.set(null);
        this.revokeError.set(this.apiErrors.toUiError(error, 'No se pudo revocar la invitación.'));
        this.load();
      },
    });
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.dismissCode();
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.dismissCode();
      this.load(this.page() + 1);
    }
  }

  canRevoke(invitation: InvitationSummary): boolean {
    return invitation.revokedAt === null && invitation.usedAt === null;
  }
}
