import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'rf-forbidden-page',
  imports: [RouterLink],
  template: `
    <section class="error-page" aria-labelledby="forbidden-title">
      <span aria-hidden="true">403</span>
      <h1 id="forbidden-title">Acceso restringido</h1>
      <p>Tu perfil no tiene autorizaci&oacute;n para utilizar este m&oacute;dulo.</p>
      <a routerLink="/dashboard">Volver al resumen</a>
    </section>
  `,
  styles: `
    :host { display: grid; min-height: 60vh; place-items: center; }
    .error-page { width: min(100%, 32rem); padding: 2rem; border: 1px solid var(--rf-border); border-top: 5px solid var(--rf-yellow); background: #fff; text-align: center; }
    span { color: var(--rf-text-secondary); font-size: .78rem; font-weight: 800; letter-spacing: .15em; }
    h1 { margin: .5rem 0; color: var(--rf-text); font-size: clamp(2rem, 6vw, 3rem); letter-spacing: -.035em; }
    p { margin: 0 auto 1.5rem; color: var(--rf-text-secondary); line-height: 1.6; }
    a { display: inline-flex; min-height: var(--rf-control-height); align-items: center; padding: 0 1rem; border: 1px solid var(--rf-black); border-radius: var(--rf-radius); color: var(--rf-black); background: var(--rf-yellow); font-weight: 750; text-decoration: none; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenPage {}
