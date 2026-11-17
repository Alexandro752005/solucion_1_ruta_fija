import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, ElementRef, HostListener, inject, Injector, input, OnChanges, output, signal, SimpleChanges } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';
import { TopbarIconComponent } from './topbar-icon.component';
import { TopbarGroup, TopbarModel } from './topbar.model';

type Panel = 'modules' | 'actions' | 'account' | 'compact';

/** A disclosure navigation. Authorization, logout and data loading belong to the shell/pages. */
@Component({
  selector: 'rf-crm-topbar',
  imports: [RouterLink, RouterLinkActive, TopbarIconComponent],
  templateUrl: './crm-topbar.component.html',
  styleUrl: './crm-topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CrmTopbarComponent implements OnChanges {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly model = input.required<TopbarModel>();
  readonly contextLabel = input('');
  readonly sessionContextKey = input('');
  readonly accountReference = input('');
  readonly loggingOut = input(false);
  readonly logoutRequested = output<void>();

  readonly panel = signal<Panel | null>(null);
  readonly expandedGroup = signal<string | null>(null);
  readonly panelLeft = signal(16);
  readonly subpanelFlipped = signal(false);
  private readonly currentUrl = signal(this.router.url);
  readonly activeGroupId = computed(() => {
    const path = this.currentUrl().split(/[?#]/)[0];
    return this.model().groups.find(group =>
      group.associatedRoutes?.some(route => path === route || path.startsWith(`${route}/`)) ||
      group.items.some(item => item.route !== null && (path === item.route || path.startsWith(`${item.route}/`))))?.id ?? null;
  });
  private compact = window.innerWidth < 1100;

  constructor() {
    this.router.events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(event => { this.currentUrl.set(event.urlAfterRedirects); this.close(); });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['accountReference'] || changes['model'] || changes['sessionContextKey'] || changes['loggingOut']?.currentValue) this.close();
  }

  togglePanel(panel: Panel, event: Event): void {
    if (panel === 'actions' && this.model().quickActions.length === 0) return;
    if (this.panel() === panel) {
      this.close();
      return;
    }
    this.expandedGroup.set(null);
    this.panel.set(panel);
    const trigger = event.currentTarget as HTMLElement;
    const parent = this.element.nativeElement.getBoundingClientRect();
    const width = panel === 'modules' ? 320 : 272;
    this.panelLeft.set(Math.max(16, Math.min(trigger.getBoundingClientRect().left - parent.left, parent.width - width - 16)));
    this.subpanelFlipped.set(this.panelLeft() + 320 + 8 + 272 > parent.width - 16);
  }

  toggleGroup(group: TopbarGroup, focusFirst = false): void {
    if (this.expandedGroup() === group.id && !focusFirst) {
      this.expandedGroup.set(null);
      return;
    }
    this.expandedGroup.set(group.id);
    if (focusFirst) {
      afterNextRender(() => this.element.nativeElement.querySelector<HTMLElement>('[data-subpanel] a')?.focus(), { injector: this.injector });
    }
  }

  groupKeydown(event: KeyboardEvent, group: TopbarGroup): void {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.toggleGroup(group, true);
    }
  }

  close(restoreFocus = false): void {
    const panel = this.panel();
    this.panel.set(null);
    this.expandedGroup.set(null);
    if (restoreFocus && panel) this.trigger(panel)?.focus();
  }

  logout(): void {
    if (this.loggingOut()) return;
    this.close();
    this.logoutRequested.emit();
  }

  @HostListener('keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.panel()) return;
    const group = this.expandedGroup();
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      if (group) {
        this.expandedGroup.set(null);
        this.element.nativeElement.querySelector<HTMLElement>(`#topbar-group-${group}`)?.focus();
      } else this.close(true);
    } else if (event.key === 'ArrowLeft' && group && (event.target as HTMLElement).closest('[data-subpanel]')) {
      event.preventDefault();
      this.expandedGroup.set(null);
      this.element.nativeElement.querySelector<HTMLElement>(`#topbar-group-${group}`)?.focus();
    }
  }

  @HostListener('focusout', ['$event'])
  onFocusout(event: FocusEvent): void {
    if (event.relatedTarget instanceof Node && this.element.nativeElement.contains(event.relatedTarget)) return;
    this.close();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (event.target instanceof Node && !this.element.nativeElement.contains(event.target)) this.close();
  }

  @HostListener('window:resize')
  onResize(): void {
    const active = document.activeElement;
    const previousPanel = this.panel();
    const wasInPanel = active instanceof HTMLElement && !!active.closest('[data-panel]') && this.element.nativeElement.contains(active);
    const compact = window.innerWidth < 1100;
    const triggerHidden = compact !== this.compact && active instanceof HTMLElement &&
      this.element.nativeElement.contains(active) &&
      (active.closest('.desktop-navigation') !== null || active.id === 'topbar-compact-trigger');
    this.compact = compact;
    this.close();
    if (wasInPanel || triggerHidden) {
      const target: Panel = compact ? (previousPanel === 'account' ? 'account' : 'compact') : (previousPanel === 'compact' ? 'modules' : previousPanel ?? 'modules');
      this.trigger(target)?.focus();
    }
  }

  private trigger(panel: Panel): HTMLElement | null {
    return this.element.nativeElement.querySelector<HTMLElement>(`#topbar-${panel}-trigger`);
  }
}
