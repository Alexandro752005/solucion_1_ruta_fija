import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'rf-forbidden-page',
  imports: [RouterLink],
  template: `
    <section class="error-page" aria-labelledby="forbidden-title">
      <span aria-hidden="true">403</span>
      <h1 id="forbidden-title">Acceso restringido</h1>
      <p>Tu rol no tiene autorización para utilizar este módulo del CRM.</p>
      <a routerLink="/dashboard">Volver al inicio</a>
    </section>
  `,
  styles: `
    :host { display: grid; min-height: 60vh; place-items: center; }
    .error-page { max-width: 32rem; text-align: center; }
    span { color: var(--primary-500); font-size: .78rem; font-weight: 800; letter-spacing: .15em; }
    h1 { margin: .5rem 0; color: var(--ink-900); font-size: clamp(2rem, 6vw, 3.5rem); letter-spacing: -.04em; }
    p { margin: 0 auto 1.5rem; color: var(--ink-500); line-height: 1.6; }
    a { display: inline-flex; min-height: 2.7rem; align-items: center; padding: 0 1rem; border-radius: .6rem; color: #fff; background: var(--primary-700); font-weight: 700; text-decoration: none; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenPage {}
