import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { Subject, Subscription } from 'rxjs';

import { OperationRealtimeEvent } from './operations.models';
import { OperationsApiService } from './operations-api.service';

export type OperationConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

/** Canal de solo lectura para refrescar las vistas operativas tras cambios confirmados. */
@Injectable({ providedIn: 'root' })
export class OperationRealtimeService implements OnDestroy {
  private readonly api = inject(OperationsApiService);
  private readonly eventsSubject = new Subject<OperationRealtimeEvent>();
  private socket: WebSocket | undefined;
  private ticketRequestInFlight = false;
  private stopped = true;
  private retryHandle: ReturnType<typeof globalThis.setTimeout> | undefined;
  private ticketRequest: Subscription | undefined;
  private generation = 0;
  private readonly connection = signal<OperationConnectionState>('disconnected');

  readonly events = this.eventsSubject.asObservable();
  readonly connectionState = this.connection.asReadonly();

  connect(): void {
    if (!this.stopped && (this.ticketRequestInFlight || this.retryHandle !== undefined)) return;
    this.stopped = false;
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) {
      return;
    }
    this.connection.set('connecting');
    this.requestTicket();
  }

  disconnect(): void {
    this.stopped = true;
    this.generation++;
    this.connection.set('disconnected');
    this.ticketRequest?.unsubscribe();
    this.ticketRequest = undefined;
    this.ticketRequestInFlight = false;
    if (this.retryHandle !== undefined) {
      globalThis.clearTimeout(this.retryHandle);
      this.retryHandle = undefined;
    }
    this.socket?.close();
    this.socket = undefined;
  }

  ngOnDestroy(): void { this.disconnect(); }

  private requestTicket(): void {
    if (this.ticketRequestInFlight || this.stopped) {
      return;
    }
    this.ticketRequestInFlight = true;
    const generation = this.generation;
    this.ticketRequest = this.api.issueStreamTicket().subscribe({
      next: ({ ticket }) => {
        if (generation !== this.generation || this.stopped) return;
        this.ticketRequestInFlight = false;
        try { this.openSocket(ticket); } catch { this.scheduleRetry(); }
      },
      error: () => {
        if (generation !== this.generation || this.stopped) return;
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
    socket.onopen = () => { if (!this.stopped && this.socket === socket) this.connection.set('connected'); };
    socket.onmessage = (event: MessageEvent<unknown>) => { if (!this.stopped && this.socket === socket) this.handleMessage(event.data); };
    socket.onclose = () => {
      if (this.socket !== socket || this.stopped) return;
      this.socket = undefined;
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
    this.connection.set('reconnecting');
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
