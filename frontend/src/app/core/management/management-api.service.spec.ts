import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ManagementApiService } from './management-api.service';

describe('ManagementApiService conductor recovery', () => {
  let service: ManagementApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ManagementApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('authorizes recovery with evidence metadata and requires a password reset', async () => {
    const result = firstValueFrom(service.startConductorRecovery('conductor-1', {
      newEmail: 'new-driver@example.test',
      evidenceMethod: 'PRIOR_SECURE_CONTACT',
      evidenceReference: 'synthetic evidence reference',
      reason: 'synthetic account recovery reason',
      expedienteReviewed: true,
      independentEvidenceConfirmed: true,
      passwordResetRequired: true,
    }));
    const request = http.expectOne('/api/v1/users/conductor-1/conductor-recovery');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toMatchObject({
      newEmail: 'new-driver@example.test',
      evidenceMethod: 'PRIOR_SECURE_CONTACT',
      expedienteReviewed: true,
      independentEvidenceConfirmed: true,
      passwordResetRequired: true,
    });
    request.flush({
      recoveryId: 'synthetic-recovery-1',
      status: 'OTP_PENDING',
      verificationToken: 'synthetic-handoff-token',
      otpExpiresAt: '2026-10-15T12:05:00Z',
      challengeExpiresAt: '2026-10-15T12:30:00Z',
      emailSent: true,
      passwordResetRequired: true,
    });
    await expect(result).resolves.toMatchObject({
      recoveryId: 'synthetic-recovery-1',
      passwordResetRequired: true,
    });
  });

  it('scopes recovery status and cancellation to the conductor route', async () => {
    const statusResponse = {
      recoveryId: 'synthetic-recovery-1',
      organizationId: 'synthetic-organization-1',
      conductorUserId: 'conductor-1',
      authorizedByAdminId: 'admin-1',
      previousEmail: 'old-driver@example.test',
      requestedEmail: 'new-driver@example.test',
      evidenceMethod: 'IN_PERSON',
      evidenceReference: 'synthetic evidence reference',
      reason: 'synthetic account recovery reason',
      expedienteReviewed: true,
      independentEvidenceConfirmed: true,
      passwordResetRequired: true,
      status: 'CANCELLED',
      emailVerifiedAt: null,
      passwordResetExpiresAt: null,
      passwordResetConsumedAt: null,
      completedAt: null,
      createdAt: '2026-10-15T12:00:00Z',
      updatedAt: '2026-10-15T12:01:00Z',
    };

    const status = firstValueFrom(
      service.getConductorRecovery('conductor-1', 'synthetic-recovery-1'),
    );
    const statusRequest = http.expectOne(
      '/api/v1/users/conductor-1/conductor-recovery/synthetic-recovery-1',
    );
    expect(statusRequest.request.method).toBe('GET');
    statusRequest.flush(statusResponse);
    await expect(status).resolves.toMatchObject({ status: 'CANCELLED' });

    const cancel = firstValueFrom(
      service.cancelConductorRecovery('conductor-1', 'synthetic-recovery-1'),
    );
    const cancelRequest = http.expectOne(
      '/api/v1/users/conductor-1/conductor-recovery/synthetic-recovery-1/cancel',
    );
    expect(cancelRequest.request.method).toBe('POST');
    expect(cancelRequest.request.body).toEqual({});
    cancelRequest.flush(statusResponse);
    await expect(cancel).resolves.toMatchObject({ status: 'CANCELLED' });
  });
});
