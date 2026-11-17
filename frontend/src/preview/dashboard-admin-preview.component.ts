import { ChangeDetectionStrategy, Component, ElementRef, computed, signal, viewChild } from '@angular/core';
import { AdminDashboardViewComponent } from '../app/features/dashboard/admin-dashboard/admin-dashboard-view.component';
import { DashboardIconComponent } from '../app/features/dashboard/admin-dashboard/dashboard-icon.component';
import { DashboardRouteViewerComponent } from '../app/features/dashboard/admin-dashboard/dashboard-route-viewer.component';
import { DashboardPreviewScenario, dashboardFixture } from './dashboard-admin.fixtures';

/** Separate review application: intentionally has no CRM config, auth, HTTP or router. */
@Component({
  selector: 'rf-root',
  imports: [AdminDashboardViewComponent, DashboardIconComponent, DashboardRouteViewerComponent],
  templateUrl: './dashboard-admin-preview.component.html',
  styleUrl: './dashboard-admin-preview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardAdminPreviewComponent {
  readonly scenario = signal<DashboardPreviewScenario>('normal');
  readonly selectedDate = signal('2026-10-26');
  readonly model = computed(() => dashboardFixture(this.scenario(), this.selectedDate()));
  readonly dialogMode = signal<'navigation' | 'export' | 'route'>('navigation');
  readonly destination = signal('');
  readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('previewDialog');

  changeScenario(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (['normal', 'activity', 'critical', 'unavailable', 'loading'].includes(value)) this.scenario.set(value as DashboardPreviewScenario);
  }

  open(mode: 'navigation' | 'export' | 'route', destination = ''): void {
    this.dialogMode.set(mode);
    this.destination.set(destination);
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }
}
