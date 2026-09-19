import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { ModalComponent } from './modal.component';

describe('ModalComponent', () => {
  it('bloquea el desplazamiento mientras está abierto y limpia el estado al destruirse', () => {
    const fixture = TestBed.createComponent(ModalComponent);
    fixture.componentRef.setInput('title', 'Prueba');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    expect(document.body.classList.contains('rf-modal-open')).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();

    fixture.destroy();
    expect(document.body.classList.contains('rf-modal-open')).toBe(false);
  });

  it('emite cierre con la tecla Escape', () => {
    const fixture = TestBed.createComponent(ModalComponent);
    const closeSpy = vi.fn();
    fixture.componentInstance.closed.subscribe(closeSpy);
    fixture.componentRef.setInput('title', 'Prueba');
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closeSpy).toHaveBeenCalledOnce();
    fixture.destroy();
  });
});
