import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { OperationsApiService } from './operations-api.service';
import { OperationRealtimeService } from './operation-realtime.service';

class ControlledWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 3;
  static instances: ControlledWebSocket[] = [];

  readonly url: string;
  readyState = ControlledWebSocket.CONNECTING;
  onmessage: ((event: MessageEvent<unknown>) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onopen: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    ControlledWebSocket.instances.push(this);
  }

  close(): void {
    this.readyState = ControlledWebSocket.CLOSED;
    this.onclose?.();
  }

  emit(payload: unknown): void {
    this.onmessage?.(new MessageEvent('message', { data: payload }));
  }
}

describe('OperationRealtimeService', () => {
  const operationsApi = {
    issueStreamTicket: vi.fn(),
  };
  let service: OperationRealtimeService;

  beforeEach(() => {
    vi.clearAllMocks();
    ControlledWebSocket.instances = [];
    operationsApi.issueStreamTicket.mockReturnValue(of({
      ticket: 'single-use-ticket',
      expiresAt: '2026-09-21T23:00:00Z',
    }));
    vi.stubGlobal('WebSocket', ControlledWebSocket);
    TestBed.configureTestingModule({
      providers: [
        OperationRealtimeService,
        { provide: OperationsApiService, useValue: operationsApi },
      ],
    });
    service = TestBed.inject(OperationRealtimeService);
  });

  afterEach(() => {
    service.disconnect();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('abre el canal del mismo origen con un ticket efímero y publica eventos válidos', () => {
    const received: unknown[] = [];
    service.events.subscribe((event) => received.push(event));

    service.connect();

    expect(operationsApi.issueStreamTicket).toHaveBeenCalledTimes(1);
    expect(ControlledWebSocket.instances).toHaveLength(1);
    const socket = ControlledWebSocket.instances[0];
    const target = new URL(socket.url);
    expect(target.protocol).toBe('ws:');
    expect(target.pathname).toBe('/ws/operations');
    expect(target.searchParams.get('ticket')).toBe('single-use-ticket');

    socket.emit(JSON.stringify({
      event: 'stream.ready',
      occurredAt: '2026-09-21T22:00:00Z',
      data: {},
    }));

    expect(received).toEqual([{
      event: 'stream.ready',
      occurredAt: '2026-09-21T22:00:00Z',
      data: {},
    }]);
  });

  it('descarta mensajes inválidos y no reabre el socket tras desconexión explícita', () => {
    const received: unknown[] = [];
    service.events.subscribe((event) => received.push(event));
    service.connect();

    const socket = ControlledWebSocket.instances[0];
    socket.emit('not-json');
    socket.emit(JSON.stringify({ event: 'missing-fields' }));
    expect(received).toEqual([]);

    service.disconnect();
    expect(socket.readyState).toBe(ControlledWebSocket.CLOSED);
    expect(ControlledWebSocket.instances).toHaveLength(1);
  });

  it('confirma conexión solo al abrirse el socket y vuelve a desconectado al cerrar', () => {
    expect(service.connectionState()).toBe('disconnected'); service.connect();
    expect(service.connectionState()).toBe('connecting'); service.connect();
    expect(operationsApi.issueStreamTicket).toHaveBeenCalledTimes(1);
    const socket = ControlledWebSocket.instances[0]; socket.readyState = ControlledWebSocket.OPEN; socket.onopen?.();
    expect(service.connectionState()).toBe('connected'); service.disconnect(); expect(service.connectionState()).toBe('disconnected');
  });
  it('señala reconexión y espera cinco segundos sin multiplicar tickets', () => {
    vi.useFakeTimers(); service.connect(); ControlledWebSocket.instances[0].close();
    expect(service.connectionState()).toBe('reconnecting'); service.connect();
    vi.advanceTimersByTime(4999); expect(operationsApi.issueStreamTicket).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(1); expect(operationsApi.issueStreamTicket).toHaveBeenCalledTimes(2);
    expect(ControlledWebSocket.instances).toHaveLength(2);
  });
  it('cancela el ticket pendiente y descarta el socket anterior al cambiar de ciclo', () => {
    const ticket = new Subject<{ticket:string;expiresAt:string}>(); operationsApi.issueStreamTicket.mockReturnValueOnce(ticket);
    service.connect(); service.disconnect(); ticket.next({ticket:'obsolete',expiresAt:'2026-10-26T12:00:00Z'});
    expect(ControlledWebSocket.instances).toHaveLength(0); service.connect(); const old = ControlledWebSocket.instances[0];
    service.disconnect(); service.connect(); old.onopen?.(); old.emit(JSON.stringify({event:'driver.changed',occurredAt:'now',data:{}}));
    expect(service.connectionState()).toBe('connecting');
  });
  it('expone reconexión tras un fallo de ticket y elimina el reintento al salir', () => {
    vi.useFakeTimers(); operationsApi.issueStreamTicket.mockReturnValueOnce(throwError(() => new Error('SYNTHETIC')));
    service.connect(); expect(service.connectionState()).toBe('reconnecting'); service.disconnect();
    vi.advanceTimersByTime(6000); expect(operationsApi.issueStreamTicket).toHaveBeenCalledTimes(1); expect(service.connectionState()).toBe('disconnected');
  });
});
