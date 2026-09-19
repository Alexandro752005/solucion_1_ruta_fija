import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

export type ModalSize = 'small' | 'medium' | 'large';

@Component({
  selector: 'rf-modal',
  templateUrl: './modal.component.html',
  styleUrl: './modal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModalComponent implements OnChanges, AfterViewChecked, OnDestroy {
  private static nextId = 0;
  private static readonly openModalIds = new Set<number>();
  private readonly instanceId = ++ModalComponent.nextId;
  private focusRequested = false;
  private returnFocus: HTMLElement | null = null;

  @ViewChild('dialog') private dialog?: ElementRef<HTMLElement>;

  @Input() open = false;
  @Input({ required: true }) title = '';
  @Input() size: ModalSize = 'medium';
  @Input() closeLabel = 'Cerrar ventana';
  @Output() readonly closed = new EventEmitter<void>();

  readonly titleId = `rf-modal-title-${this.instanceId}`;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['open']) {
      return;
    }

    if (this.open) {
      this.returnFocus = document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
      this.focusRequested = true;
      ModalComponent.openModalIds.add(this.instanceId);
      this.syncBodyScroll();
    } else {
      ModalComponent.openModalIds.delete(this.instanceId);
      this.syncBodyScroll();
      this.returnFocus?.focus();
      this.returnFocus = null;
    }
  }

  ngOnDestroy(): void {
    ModalComponent.openModalIds.delete(this.instanceId);
    this.syncBodyScroll();
  }

  ngAfterViewChecked(): void {
    if (!this.focusRequested || !this.dialog) {
      return;
    }

    this.focusRequested = false;
    const focusable = this.focusableElements();
    (focusable[0] ?? this.dialog.nativeElement).focus();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open) {
      this.closed.emit();
    }
  }

  close(): void {
    this.closed.emit();
  }

  backdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }

  trapFocus(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return;
    }

    const focusable = this.focusableElements();
    if (focusable.length === 0) {
      event.preventDefault();
      this.dialog?.nativeElement.focus();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private focusableElements(): HTMLElement[] {
    const selector = [
      'button:not([disabled])',
      '[href]',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ].join(',');
    return Array.from(this.dialog?.nativeElement.querySelectorAll<HTMLElement>(selector) ?? [])
      .filter((element) => !element.hasAttribute('hidden'));
  }

  private syncBodyScroll(): void {
    document.body.classList.toggle('rf-modal-open', ModalComponent.openModalIds.size > 0);
  }
}
