import { inject, Injectable } from '@angular/core';
import { Subject } from 'rxjs';

import { OperationRealtimeEvent } from './operations.models';
import { OperationsApiService } from './operations-api.service';

/** Canal de solo lectura para refrescar las vistas operativas tras cambios confirmados. */
@Injectable({ providedIn: 'root' })
export class OperationRealtimeService {
  private readonly api = inject(OperationsApiService);
  private readonly eventsSubject = new Subject<OperationRealtimeEvent>();
  private socket: WebSocket | undefined;
  private ticketRequestInFlight = false;
  private stopped = true;
  private retryHandle: ReturnType<typeof globalThis.setTimeout> | undefined;

  readonly events = this.eventsSubject.asObservable();

  connect(): void {
    this.stopped = false;
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) {
      return;
    }
    this.requestTicket();
  }

  disconnect(): void {
    this.stopped = true;
    if (this.retryHandle !== undefined) {
      globalThis.clearTimeout(this.retryHandle);
      this.retryHandle = undefined;
    }
    this.socket?.close();
    this.socket = undefined;
  }

  private requestTicket(): void {
    if (this.ticketRequestInFlight || this.stopped) {
      return;
    }
    this.ticketRequestInFlight = true;
    this.api.issueStreamTicket().subscribe({
      next: ({ ticket }) => {
        this.ticketRequestInFlight = false;
        if (!this.stopped) {
          this.openSocket(ticket);
        }
      },
      error: () => {
        this.ticketRequestInFlight = false;
        this.scheduleRetry();
      },
    });
  }

  private openSocket(ticket: string): void {
    const socketUrl = new URL('/ws/operations', globalThis.location.origin);
    socketUrl.protocol = globalThis.location.protocol === 'https:' ? 'wss:' : 'ws:';
    socketUrl.searchParams.set('ticket', ticket);

    const socket = new WebSocket(socketUrl.toString());
    this.socket = socket;
    socket.onmessage = (event: MessageEvent<unknown>) => this.handleMessage(event.data);
    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = undefined;
      }
      this.scheduleRetry();
    };
    socket.onerror = () => socket.close();
  }

  private handleMessage(payload: unknown): void {
    if (typeof payload !== 'string') {
      return;
    }
    try {
      const candidate: unknown = JSON.parse(payload);
      if (!this.isEvent(candidate)) {
        return;
      }
      this.eventsSubject.next(candidate);
    } catch {
      // Un mensaje malformado no altera el estado local del CRM.
    }
  }

  private scheduleRetry(): void {
    if (this.stopped || this.retryHandle !== undefined) {
      return;
    }
    this.retryHandle = globalThis.setTimeout(() => {
      this.retryHandle = undefined;
      this.requestTicket();
    }, 5_000);
  }

  private isEvent(value: unknown): value is OperationRealtimeEvent {
    if (typeof value !== 'object' || value === null) {
      return false;
    }
    const record = value as Record<string, unknown>;
    return (
      typeof record['event'] === 'string' &&
      typeof record['occurredAt'] === 'string' &&
      typeof record['data'] === 'object' &&
      record['data'] !== null &&
      !Array.isArray(record['data'])
    );
  }
}
