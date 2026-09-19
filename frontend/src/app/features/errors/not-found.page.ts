import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'rf-not-found-page',
  imports: [RouterLink],
  template: `
    <main class="error-page" aria-labelledby="not-found-title">
      <span aria-hidden="true">404</span>
      <h1 id="not-found-title">P&aacute;gina no encontrada</h1>
      <p>La direcci&oacute;n solicitada no forma parte del sistema.</p>
      <a routerLink="/">Ir al inicio</a>
    </main>
  `,
  styles: `
    :host { display: grid; min-height: 100dvh; padding: 1.5rem; place-items: center; background: var(--rf-background-soft); }
    .error-page { width: min(100%, 32rem); padding: 2rem; border: 1px solid var(--rf-border); border-top: 5px solid var(--rf-yellow); background: #fff; text-align: center; }
    span { color: var(--rf-text-secondary); font-size: .78rem; font-weight: 800; letter-spacing: .15em; }
    h1 { margin: .5rem 0; color: var(--rf-text); font-size: clamp(2rem, 6vw, 3rem); letter-spacing: -.035em; }
    p { margin: 0 auto 1.5rem; color: var(--rf-text-secondary); line-height: 1.6; }
    a { display: inline-flex; min-height: var(--rf-control-height); align-items: center; padding: 0 1rem; border: 1px solid var(--rf-black); border-radius: var(--rf-radius); color: var(--rf-black); background: var(--rf-yellow); font-weight: 750; text-decoration: none; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {}
