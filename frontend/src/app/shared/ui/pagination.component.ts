import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'rf-pagination',
  template: `
    <nav class="rf-pagination" aria-label="Paginaci&oacute;n">
      <p>P&aacute;gina {{ currentPage }} de {{ normalizedTotalPages }} &middot; {{ totalItems }} registros</p>
      <div>
        <button class="management-button compact" type="button" (click)="previous.emit()" [disabled]="page <= 0">
          Anterior
        </button>
        <button class="management-button compact" type="button" (click)="next.emit()" [disabled]="page + 1 >= totalPages">
          Siguiente
        </button>
      </div>
    </nav>
  `,
  styles: `
    :host { display: block; }
    .rf-pagination { display: flex; align-items: center; justify-content: space-between; margin-top: 1rem; color: var(--rf-text-secondary); font-size: .82rem; gap: 1rem; }
    .rf-pagination p { margin: 0; }
    .rf-pagination div { display: flex; gap: .5rem; }
    @media (max-width: 540px) { .rf-pagination { align-items: stretch; flex-direction: column; } .rf-pagination div button { flex: 1; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginationComponent {
  @Input() page = 0;
  @Input() totalPages = 0;
  @Input() totalItems = 0;
  @Output() readonly previous = new EventEmitter<void>();
  @Output() readonly next = new EventEmitter<void>();

  get currentPage(): number {
    return this.totalPages === 0 ? 1 : this.page + 1;
  }

  get normalizedTotalPages(): number {
    return Math.max(this.totalPages, 1);
  }
}
