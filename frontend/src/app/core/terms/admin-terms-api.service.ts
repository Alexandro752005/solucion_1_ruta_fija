import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, throwError } from 'rxjs';

import { RuntimeConfigService } from '../config/runtime-config.service';
import {
  AdminTermsAcceptance,
  AdminTermsDocument,
} from './admin-terms.models';

export class SyntheticTermsUnavailableError extends Error {
  constructor() {
    super('Synthetic terms are available only in the test environment.');
    this.name = 'SyntheticTermsUnavailableError';
  }
}

@Injectable({ providedIn: 'root' })
export class AdminTermsApiService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfig = inject(RuntimeConfigService);

  getCurrentTerms(): Observable<AdminTermsDocument> {
    return this.http
      .get<AdminTermsDocument>(
        this.runtimeConfig.apiUrl('/admin/terms/current'),
      )
      .pipe(map((document) => this.requireDisplayableDocument(document)));
  }

  acceptCurrentTerms(
    document: AdminTermsDocument,
  ): Observable<AdminTermsAcceptance> {
    if (document.synthetic && !this.isTestEnvironment()) {
      return throwError(() => new SyntheticTermsUnavailableError());
    }

    return this.http.post<AdminTermsAcceptance>(
      this.runtimeConfig.apiUrl('/admin/terms/acceptances'),
      {
        version: document.version,
        contentSha256: document.contentSha256,
        accepted: true,
      },
    );
  }

  private requireDisplayableDocument(
    document: AdminTermsDocument,
  ): AdminTermsDocument {
    if (document.synthetic && !this.isTestEnvironment()) {
      throw new SyntheticTermsUnavailableError();
    }
    return document;
  }

  private isTestEnvironment(): boolean {
    return this.runtimeConfig.config().environment === 'test';
  }
}
