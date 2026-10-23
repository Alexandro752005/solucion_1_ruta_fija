import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AssignedRoute, RoutesApiService, RouteSummary } from '../../core/routes/routes-api.service';
import { Assignment } from '../../core/operations/operations.models';
import { ApiErrorService } from '../../core/api/api-error.service';

@Component({
  selector: 'rf-assignment-route-panel', imports: [FormsModule], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<section class="management-detail"><h3>Ruta de esta asignación</h3>
    @if (error()) { <p role="alert">{{ error() }}</p> }
    @if (view(); as v) {
      @if (v.route; as r) { <p>{{ r.name }} · revisión {{ r.revision }} · {{ r.contentSha256 }}</p><ol>@for (s of r.stops; track s.key) { <li>{{ s.kind }} · {{ s.name }}</li> }</ol> }
      @else { <p>Ruta histórica sin versión registrada. No se reconstruye desde el catálogo actual.</p> }
      @if (v.declaration; as d) { <p>Ruta finalizada declarada: {{ d.declaredAt }} · Terminal: {{ d.terminalResult }}. No acredita todo el recorrido GPS.</p> }
      @if(v.declaration?.terminalResult==='DISCREPANCY'){<p role="alert">Observación del terminal: requiere revisión ADMIN. No implica fraude ni acredita el recorrido completo.</p>}
      @else { <p>Sin declaración de ruta finalizada por CONDUCTOR.</p> }
      @if (v.replacementAssignmentId) { <p>Reemplazo con nueva aceptación: ASIGNACIÓN-{{ v.replacementAssignmentId }}</p> }
    }
    <button type="button" (click)="read()" [disabled]="busy()">Consultar ruta persistida</button>
    @if (canReplace()) {
      <p>El cambio retira la oferta y crea otra del mismo servicio, pendiente de nueva aceptación. Conserva la aceptación y ruta anteriores; un fallo revierte ambas operaciones.</p>
      <button type="button" (click)="loadChoices()" [disabled]="busy() || uncertain()">Elegir nueva versión vigente</button>
      <select [(ngModel)]="replacementVersion" [disabled]="busy() || uncertain()"><option value="">Selecciona ruta</option>@for (r of choices(); track r.id) { <option [value]="r.currentVersionId">{{ r.name }}</option> }</select>
      <label>Motivo sin datos personales <input [(ngModel)]="reason" maxlength="160" [disabled]="busy() || uncertain()" /></label>
      <label><input type="checkbox" [(ngModel)]="confirmed" [disabled]="busy() || uncertain()" />Confirmo el reemplazo y la nueva aceptación requerida.</label>
      <button type="button" (click)="replace()" [disabled]="busy() || uncertain()">Cambiar ruta expresamente</button>
    }
    @if (uncertain()) { <p>Resultado incierto: no repitas el reemplazo.</p><button type="button" (click)="reconcile()" [disabled]="busy()">Consultar resultado de la operación</button> }
  </section>`,
})
export class AssignmentRoutePanel {
  readonly assignment = input.required<Assignment>(); readonly replaced = output<Assignment>();
  readonly externalBlocked=input(false);readonly blocked=output<boolean>();
  private readonly api = inject(RoutesApiService); private readonly errors = inject(ApiErrorService);
  readonly view = signal<AssignedRoute | null>(null); readonly choices = signal<readonly RouteSummary[]>([]);
  readonly busy = signal(false); readonly uncertain = signal(false); readonly error = signal<string | null>(null);
  replacementVersion = ''; reason = ''; confirmed = false; private pendingKey: string | null = null; private generation = 0;
  constructor() { effect(() => { this.assignment(); this.read(); }); }
  canReplace() { const a = this.assignment(); return !this.externalBlocked() && !!a.serviceId && !a.startedAt && ['SCHEDULED','PENDING_RESPONSE'].includes(a.status) && Date.parse(a.scheduledAt) > Date.now() && !this.view()?.replacementAssignmentId; }
  read() { const id = this.assignment().id; const generation = ++this.generation;
    this.api.assigned(id).subscribe({ next: (v) => { if (generation === this.generation) { this.view.set(v); this.error.set(null); } }, error: (e: unknown) => { if (generation === this.generation) this.error.set(this.errors.toUiError(e).message); } }); }
  loadChoices() { this.api.list().subscribe({ next: (r) => this.choices.set(r.filter((x) => x.active)), error: (e: unknown) => this.error.set(this.errors.toUiError(e).message) }); }
  replace() {
    if (this.busy() || this.uncertain() || !this.canReplace() || !this.confirmed || !this.reason.trim() || !this.replacementVersion) return;
    this.pendingKey = crypto.randomUUID(); this.busy.set(true);this.blocked.emit(true);
    this.api.replace(this.assignment().id, { expectedVersion: this.assignment().version, operationKey: this.pendingKey, routeVersionId: this.replacementVersion, reason: this.reason.trim() })
      .subscribe({ next: (a) => { this.busy.set(false); this.confirmed = false;this.blocked.emit(false); this.replaced.emit(a); this.read(); }, error: (e: unknown) => { this.busy.set(false); this.confirmed = false; this.error.set(this.errors.toUiError(e).message);
        const status = typeof e === 'object' && e !== null && 'status' in e ? Number(e.status) : 0; this.uncertain.set(![400,401,403,404,409,422].includes(status));this.blocked.emit(this.uncertain()); this.read(); } });
  }
  reconcile() { if (!this.pendingKey || this.busy()) return; this.busy.set(true);
    this.api.replacementOperation(this.pendingKey).subscribe({ next: (a) => { this.uncertain.set(false); this.busy.set(false);this.blocked.emit(false); this.replaced.emit(a); this.read(); }, error: (e: unknown) => { this.busy.set(false); this.error.set(this.errors.toUiError(e).message); } }); }
}
