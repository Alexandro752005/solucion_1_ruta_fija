import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LiveMapCartographyService } from '../../core/live-map/live-map-cartography.service';
import { LiveMapCanvasComponent } from './live-map-canvas.component';
import { LiveMapFacade } from './live-map.facade';

@Component({
  selector: 'rf-live-map-page', imports: [FormsModule, DatePipe, RouterLink, LiveMapCanvasComponent],
  providers: [LiveMapFacade, LiveMapCartographyService], changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './live-map.page.html', styleUrl: './live-map.page.scss',
})
export class LiveMapPage implements OnInit, OnDestroy {
  readonly data = inject(LiveMapFacade);
  readonly cartography = inject(LiveMapCartographyService);
  readonly centerRequest = signal(0);
  ngOnInit(): void { this.data.start(); this.cartography.load(); }
  ngOnDestroy(): void { this.data.stop(); this.cartography.stop(); }
  center(id?: string): void { if (id) this.data.select(id); this.centerRequest.update(value => value + 1); }
  select(id: string): void { this.data.select(id); }
}
