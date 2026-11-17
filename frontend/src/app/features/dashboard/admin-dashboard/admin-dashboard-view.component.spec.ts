import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { dashboardFixture } from '../../../../preview/dashboard-admin.fixtures';
import { AdminDashboardViewComponent } from './admin-dashboard-view.component';
import { DashboardDonut } from './admin-dashboard.models';
import { DashboardDonutComponent } from './dashboard-donut.component';

afterEach(() => TestBed.resetTestingModule());

async function view(scenario: Parameters<typeof dashboardFixture>[0] = 'normal') {
  await TestBed.configureTestingModule({ imports: [AdminDashboardViewComponent] }).compileComponents();
  const fixture = TestBed.createComponent(AdminDashboardViewComponent);
  fixture.componentRef.setInput('model', dashboardFixture(scenario));
  fixture.detectChanges();
  return fixture;
}

async function donut(model: DashboardDonut) {
  await TestBed.configureTestingModule({ imports: [DashboardDonutComponent] }).compileComponents();
  const fixture = TestBed.createComponent(DashboardDonutComponent);
  fixture.componentRef.setInput('model', model);
  fixture.detectChanges();
  return fixture;
}

describe('Dashboard ADMIN presentation with explicit synthetic fixtures', () => {
  it('renders five metrics, six panels and three accessible donut figures', async () => {
    const fixture = await view();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelectorAll('[data-metric]')).toHaveLength(5);
    expect(root.querySelectorAll('[data-panel]')).toHaveLength(6);
    expect(root.querySelectorAll('rf-dashboard-donut figure[role=img]')).toHaveLength(3);
    expect(root.querySelector('h1')?.textContent).toBe('Hola, Administrador');
    fixture.destroy();
  });

  it('shows no service denominator as neutral, not as zero percent completion', async () => {
    const fixture = await view();
    const panel: HTMLElement = fixture.nativeElement.querySelector('[data-panel=DA-P02]');
    expect(panel.textContent).toContain('Sin servicios programados');
    expect(panel.querySelector('.center-value')?.textContent).toBe('—');
    expect(panel.querySelectorAll('.arc')).toHaveLength(0);
    fixture.destroy();
  });

  it('retains the safety fleet category even when its count is zero', async () => {
    const fixture = await view();
    const panel: HTMLElement = fixture.nativeElement.querySelector('[data-panel=DA-P01]');
    expect(panel.querySelectorAll('.legend li')).toHaveLength(5);
    expect(panel.textContent).toContain('Atención segura');
    expect(panel.querySelector('.center-value')?.textContent).toBe('2');
    fixture.destroy();
  });

  it('keeps critical priorities before metrics and before scheduling priorities', async () => {
    const fixture = await view('critical');
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector<HTMLDetailsElement>('.priorities')?.open).toBe(true);
    expect(root.textContent!.indexOf('Incidencias críticas abiertas')).toBeLessThan(root.textContent!.indexOf('Conductores disponibles'));
    expect(root.textContent!.indexOf('Incidencias críticas abiertas')).toBeLessThan(root.textContent!.indexOf('Servicios próximos sin aceptación'));
    fixture.destroy();
  });

  it('does not present failed sources as zero or a favorable empty state', async () => {
    const fixture = await view('unavailable');
    const root: HTMLElement = fixture.nativeElement;
    expect(Array.from(root.querySelectorAll('.metric-value'), element => element.textContent?.trim())).toEqual(['—', '—', '—', '—', '—']);
    expect(root.querySelector('.notice-empty')).toBeNull();
    expect(root.querySelectorAll('rf-dashboard-donut figure')).toHaveLength(0);
    expect(root.textContent).toContain('No es posible confirmar los pendientes');
    fixture.destroy();
  });

  it('distinguishes loading from a successful empty response', async () => {
    const fixture = await view('loading');
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('.notice-empty')).toBeNull();
    expect(root.textContent).toContain('Consultando indicadores');
    expect(root.querySelectorAll('.arc')).toHaveLength(0);
    fixture.destroy();
  });

  it('keeps current open incidents independent of the selected reported-day cohort', async () => {
    const fixture = await view('activity');
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('[data-metric=DA-K05] .metric-value')?.textContent).toBe('8');
    expect(root.querySelector('[data-panel=DA-P04] .center-value')?.textContent).toBe('6');
    expect(root.querySelector('.notice-empty')).toBeNull();
    fixture.destroy();
  });

  it('emits an explicit date selection and ignores dates after the operational day', async () => {
    const fixture = await view();
    const dates: string[] = [];
    fixture.componentInstance.selectDate.subscribe(value => dates.push(value));
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type=date]');
    input.value = '2026-10-25'; input.dispatchEvent(new Event('change'));
    input.value = '2026-10-27'; input.dispatchEvent(new Event('change'));
    expect(dates).toEqual(['2026-10-25']);
    fixture.destroy();
  });

  it('emits navigation and export intents only after explicit activation', async () => {
    const fixture = await view();
    const navigation = vi.fn(); const exportRequest = vi.fn(); const routeRequest = vi.fn();
    fixture.componentInstance.navigate.subscribe(navigation);
    fixture.componentInstance.requestExport.subscribe(exportRequest);
    fixture.componentInstance.requestRoute.subscribe(routeRequest);
    expect(navigation).not.toHaveBeenCalled(); expect(exportRequest).not.toHaveBeenCalled();
    (fixture.nativeElement.querySelector('[data-metric=DA-K01]') as HTMLButtonElement).click();
    (fixture.nativeElement.querySelector('.toolbar-button') as HTMLButtonElement).click();
    (fixture.nativeElement.querySelector('.route-preview') as HTMLButtonElement).click();
    expect(navigation).toHaveBeenCalledExactlyOnceWith('/drivers');
    expect(exportRequest).toHaveBeenCalledOnce(); expect(routeRequest).toHaveBeenCalledOnce();
    fixture.destroy();
  });

  it('shows coded driver references and makes the full reference accessible', async () => {
    const fixture = await view();
    const code: HTMLElement = fixture.nativeElement.querySelector('.driver-list strong');
    expect(code.textContent).toContain('CONDUCTOR-…');
    expect(code.getAttribute('aria-label')).toBe('CONDUCTOR-00000000-0000-4000-8000-000000000001');
    expect(code.title).toBe(code.getAttribute('aria-label'));
    fixture.destroy();
  });
});

describe('Dashboard donut invariants', () => {
  it('draws only the two canonical operation partitions and preserves all five result labels', async () => {
    const fixture = await donut(dashboardFixture('activity').operation);
    expect(fixture.nativeElement.querySelectorAll('.arc')).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('.legend li')).toHaveLength(5);
    expect(fixture.nativeElement.textContent).toContain('Cancelados');
    expect(fixture.nativeElement.textContent).toContain('Relevados');
    fixture.destroy();
  });

  it.each(['negative', 'mismatched-total'] as const)('rejects %s counts rather than drawing a misleading success', async kind => {
    const base = dashboardFixture().fleet;
    const fixture = await donut({ ...base, total: kind === 'negative' ? -1 : 3 });
    expect(fixture.nativeElement.textContent).toContain('Información no verificable');
    expect(fixture.nativeElement.querySelector('figure')).toBeNull();
    fixture.destroy();
  });

  it('keeps displayed rounded thirds at 99.9 rather than inflating a category', async () => {
    const slices = [{ label: 'A', value: 1, tone: 'green' }, { label: 'B', value: 1, tone: 'blue' }, { label: 'C', value: 1, tone: 'amber' }] as const;
    const fixture = await donut({ ...dashboardFixture().fleet, total: 3, center: '3', segments: slices, legend: slices });
    expect(Array.from(fixture.nativeElement.querySelectorAll('.legend-percent'), (element: Element) => element.textContent)).toEqual(['33.3 %', '33.3 %', '33.3 %']);
    fixture.destroy();
  });
});
