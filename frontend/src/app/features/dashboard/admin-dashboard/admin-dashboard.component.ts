import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { AdminDashboardViewComponent } from './admin-dashboard-view.component';
import { AdminDashboardFacade } from './admin-dashboard.facade';
import { DashboardReportFacade } from './dashboard-report.facade';
import { DashboardReportExportComponent } from './dashboard-report-export.component';
import { DashboardRouteViewerComponent } from './dashboard-route-viewer.component';
import { DashboardLiveUpdates } from './dashboard-live-updates.service';
import { DashboardConnectionComponent } from './dashboard-connection.component';

@Component({
  selector: 'rf-admin-dashboard',
  imports: [AdminDashboardViewComponent, DashboardReportExportComponent, DashboardRouteViewerComponent, DashboardConnectionComponent],
  providers: [AdminDashboardFacade, DashboardReportFacade, DashboardLiveUpdates],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<rf-dashboard-connection/>
    <rf-admin-dashboard-view [model]="data.view()" (navigate)="navigate($event)" (selectDate)="data.selectDate($event)" (selectRoute)="data.selectRoute($event)" (routePage)="data.routePage($event)" (retryRoutes)="data.loadRoutes()" (requestActivity)="data.loadActivity()" (requestExport)="open('reports')" (requestRoute)="open('route')"/>
    <dialog #detailDialog aria-labelledby="dashboard-detail-title" (close)="closed()" (keydown)="trapFocus($event)"><header><h2 id="dashboard-detail-title">{{ mode() === 'reports' ? 'Exportar reporte de servicios' : 'Visor de ruta registrada' }}</h2><button type="button" aria-label="Cerrar detalle" (click)="close()">×</button></header>
      @if (dialogOpen()) {
        @if (mode() === 'reports') { <rf-dashboard-report-export/> }
        @else { <rf-dashboard-route-viewer [route]="data.view().route"/><button type="button" (click)="navigate('/routes')">Ver rutas</button> }
      }
    </dialog>`,
  styles: `:host{display:block;min-width:0}dialog{width:min(520px,calc(100% - 32px));max-height:85dvh;overflow:auto;border:1px solid #e5eaf2;border-radius:14px;padding:24px;color:#111827;font-size:14px}dialog::backdrop{background:#101e3a55}header{display:flex;justify-content:space-between;gap:12px}h2{margin:0;font-size:20px}button{min-height:44px;padding:8px 12px;border:1px solid #d9e2f0;border-radius:8px;background:#f3f7ff;color:#1d4ed8;cursor:pointer;font:inherit}header button{min-width:44px;font-size:22px}button:focus-visible{outline:3px solid #1d4ed8;outline-offset:3px}`,
})
export class AdminDashboardComponent implements OnInit, OnDestroy {
  readonly data = inject(AdminDashboardFacade);
  private readonly reports = inject(DashboardReportFacade);
  private readonly router = inject(Router);
  private readonly live = inject(DashboardLiveUpdates);
  private opener: HTMLElement | null = null;
  readonly mode = signal<'reports' | 'route'>('reports');
  readonly dialogOpen = signal(false);
  readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('detailDialog');
  private readonly destinations = new Set(['/users', '/organization', '/groups', '/drivers', '/vehicles', '/assignments', '/incidents', '/announcements', '/onboarding-requests', '/notices', '/reports', '/routes', '/audit', '/admin-terms']);

  ngOnInit(): void { this.live.start(); }
  ngOnDestroy(): void { this.live.stop(); }
  navigate(destination: string): void {
    if (!this.destinations.has(destination)) return;
    this.close(); void this.router.navigateByUrl(destination);
  }
  open(mode: 'reports' | 'route'): void {
    if (this.dialog().nativeElement.open) return;
    this.opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.mode.set(mode); this.dialogOpen.set(true);
    if (mode === 'reports') this.reports.open(this.data.view().selectedDate, this.data.view().dateContextAvailable === false ? '' : this.data.view().zoneId);
    this.dialog().nativeElement.showModal();
  }
  close(): void { if (this.dialog().nativeElement.open) this.dialog().nativeElement.close(); }
  trapFocus(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const dialog = this.dialog().nativeElement;
    const controls = [...dialog.querySelectorAll<HTMLElement>('button,a[href],input,select,textarea,[tabindex]')]
      .filter(control => control.tabIndex >= 0 && !control.matches(':disabled') && control.getClientRects().length > 0);
    const target = event.shiftKey ? controls.at(-1) : controls[0];
    const edge = event.shiftKey ? controls[0] : controls.at(-1);
    if (target && (dialog.ownerDocument.activeElement === edge || !dialog.contains(dialog.ownerDocument.activeElement))) {
      event.preventDefault(); target.focus();
    }
  }
  closed(): void { this.dialogOpen.set(false); this.opener?.isConnected && this.opener.focus(); this.opener = null; }
}
