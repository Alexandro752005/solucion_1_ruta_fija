import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'rf-not-found-page',
  imports: [RouterLink],
  template: `
    <main class="error-page" aria-labelledby="not-found-title">
      <span aria-hidden="true">404</span>
      <h1 id="not-found-title">Página no encontrada</h1>
      <p>La dirección solicitada no forma parte del CRM.</p>
      <a routerLink="/">Ir al inicio</a>
    </main>
  `,
  styles: `
    :host { display: grid; min-height: 100dvh; place-items: center; padding: 1.5rem; background: var(--surface-muted); }
    .error-page { max-width: 32rem; text-align: center; }
    span { color: var(--primary-500); font-size: .78rem; font-weight: 800; letter-spacing: .15em; }
    h1 { margin: .5rem 0; color: var(--ink-900); font-size: clamp(2rem, 6vw, 3.5rem); letter-spacing: -.04em; }
    p { margin: 0 auto 1.5rem; color: var(--ink-500); line-height: 1.6; }
    a { display: inline-flex; min-height: 2.7rem; align-items: center; padding: 0 1rem; border-radius: .6rem; color: #fff; background: var(--primary-700); font-weight: 700; text-decoration: none; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {}
