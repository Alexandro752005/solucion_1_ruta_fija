import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Subscription, timeout } from 'rxjs';
import { validateTileUrl } from './live-map.models';

/** Configuration is local; an invalid configuration disables tiles rather than choosing a different provider. */
@Injectable()
export class LiveMapCartographyService {
  private readonly http = inject(HttpClient);
  readonly tileUrl = signal<string | null>(null);
  readonly unavailable = signal(false);
  private request?: Subscription;
  load(): void {
    this.request?.unsubscribe(); this.unavailable.set(false); this.tileUrl.set(null);
    this.request = this.http.get<unknown>('/config/live-map.json').pipe(timeout(5000)).subscribe({
      next: config => {
        const value = typeof config === 'object' && config !== null && 'tileUrl' in config ? config.tileUrl : null;
        const url = validateTileUrl(value); this.tileUrl.set(url); this.unavailable.set(url === null);
      },
      error: () => this.unavailable.set(true),
    });
  }
  stop(): void { this.request?.unsubscribe(); this.tileUrl.set(null); }
}
