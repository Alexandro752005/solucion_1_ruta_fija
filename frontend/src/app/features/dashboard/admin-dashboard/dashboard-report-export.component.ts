import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DashboardReportFacade } from './dashboard-report.facade';

@Component({
  selector: 'rf-dashboard-report-export', changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-report-export.component.html',
  styles: `:host{display:grid;min-width:0;gap:12px;color:#334155;font-size:14px;line-height:1.5}p,h3{margin:0}.scope{padding:12px;background:#f3f7ff;border-radius:10px}.actions{display:flex;flex-wrap:wrap;gap:8px}button{min-height:44px;padding:8px 12px;border:1px solid #d9e2f0;border-radius:8px;background:#f3f7ff;color:#1d4ed8;font:inherit;cursor:pointer}button:disabled{color:#64748b;background:#f8fafc;cursor:default}button:focus-visible{outline:3px solid #1d4ed8;outline-offset:2px}.error{color:#991b1b}.pending{padding:12px;border:1px solid #d6a75b;border-radius:8px;background:#fffbeb}.emission{display:grid;min-width:0;gap:10px;padding-top:12px;border-top:1px solid #e5eaf2}.hash,.reference{overflow-wrap:anywhere}.muted{color:#52627a;font-size:12px}button[aria-pressed=true]{border-color:#1d4ed8;background:#eaf1ff}`,
})
export class DashboardReportExportComponent {
  readonly report = inject(DashboardReportFacade);
  readonly formats = ['csv', 'xlsx', 'pdf'] as const;
}
