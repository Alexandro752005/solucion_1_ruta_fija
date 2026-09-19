import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

import { ModalComponent } from './modal.component';

@Component({
  selector: 'rf-confirm-dialog',
  imports: [ModalComponent],
  template: `
    <rf-modal [open]="open" [title]="title" size="small" (closed)="cancelled.emit()">
      <p class="rf-confirm-message">{{ message }}</p>
      <div modal-actions>
        <button class="management-button" type="button" (click)="cancelled.emit()">{{ cancelLabel }}</button>
        <button class="management-button" [class.danger]="danger" [class.primary]="!danger" type="button" (click)="confirmed.emit()">
          {{ confirmLabel }}
        </button>
      </div>
    </rf-modal>
  `,
  styles: `.rf-confirm-message { margin: 0; color: var(--rf-text-secondary); font-size: 1rem; line-height: 1.55; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  @Input() open = false;
  @Input() title = 'Confirmar acción';
  @Input() message = '';
  @Input() confirmLabel = 'Confirmar';
  @Input() cancelLabel = 'Cancelar';
  @Input() danger = false;
  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();
}
