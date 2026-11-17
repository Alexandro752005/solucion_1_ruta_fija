import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { OperationRealtimeService } from '../../../core/operations/operation-realtime.service';

@Component({
  selector: 'rf-dashboard-connection', changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p role="status"><span [class.connected]="realtime.connectionState() === 'connected'" aria-hidden="true"></span>{{ labels[realtime.connectionState()] }} · actualización de respaldo cada 60 s con la pestaña visible. Los paneles conservan cortes independientes.</p>`,
  styles: `:host{display:block;margin:0 0 10px}p{display:flex;align-items:baseline;gap:7px;margin:0;color:#52627a;font-size:12px;line-height:1.5}span{display:inline-block;flex:0 0 7px;height:7px;border-radius:50%;background:#b45309}.connected{background:#059669}`,
})
export class DashboardConnectionComponent {
  readonly realtime = inject(OperationRealtimeService);
  readonly labels = { disconnected: 'Canal de cambios desconectado', connecting: 'Conectando canal de cambios', connected: 'Canal de cambios conectado', reconnecting: 'Reconectando canal de cambios' } as const;
}
