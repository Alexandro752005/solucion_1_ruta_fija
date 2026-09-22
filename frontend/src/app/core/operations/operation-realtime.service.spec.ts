import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
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
});
