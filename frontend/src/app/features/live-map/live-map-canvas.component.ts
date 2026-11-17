import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, InjectionToken, OnDestroy, ViewEncapsulation, afterNextRender, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type * as Leaflet from 'leaflet';
import { LiveMapUnit } from '../../core/live-map/live-map.models';
import { clusterLiveMapUnits } from './live-map-clusters';

export const LIVE_MAP_LEAFLET = new InjectionToken<() => Promise<typeof Leaflet>>('Local Leaflet loader', {providedIn:'root',factory:()=>()=>import('leaflet/dist/leaflet-src.esm.js')});
export const LIVE_MAP_STYLES = new InjectionToken<() => Promise<void>>('Local Leaflet stylesheet', {providedIn:'root',factory:()=>{
  let pending:Promise<void>|undefined;
  return ()=>pending ??= new Promise<void>((resolve,reject)=>{
    const link=document.createElement('link');link.rel='stylesheet';link.href='/vendor/leaflet/leaflet.css';link.id='rf-live-map-leaflet-style';
    const timeout=setTimeout(()=>{link.remove();pending=undefined;reject(new Error('Local map stylesheet unavailable'));},5000);
    link.onload=()=>{clearTimeout(timeout);resolve();};link.onerror=()=>{clearTimeout(timeout);link.remove();pending=undefined;reject(new Error('Local map stylesheet unavailable'));};
    document.head.append(link);
  });
}});

@Component({
  selector: 'rf-live-map-canvas', changeDetection: ChangeDetectionStrategy.OnPush, encapsulation: ViewEncapsulation.None,
  template: `<div class="rf-live-map-canvas"><div #canvas class="rf-live-map-canvas__map" role="region" aria-label="Mapa de unidades en servicio; lista alternativa a continuación"></div>
    @if (failed()) { <p class="rf-live-map-canvas__error" role="status">La cartografía no está disponible. Consulta la lista de unidades.</p> }
    <div class="rf-live-map-canvas__attribution">© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap contributors</a> · <a href="/licenses/Leaflet-LICENSE.txt" target="_blank" rel="noopener">Leaflet</a></div></div>`,
  styleUrl: './live-map-canvas.component.scss',
})
export class LiveMapCanvasComponent implements OnDestroy {
  readonly units = input.required<readonly LiveMapUnit[]>();
  readonly selectedId = input('');
  readonly centerRequest = input(0);
  readonly tileUrl = input.required<string>();
  readonly selectUnit = output<string>();
  readonly failed = signal(false);
  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private readonly destroyed = inject(DestroyRef);
  private readonly loadLeaflet = inject(LIVE_MAP_LEAFLET);
  private readonly loadStyles = inject(LIVE_MAP_STYLES);
  private leaflet?: typeof Leaflet;
  private map?: Leaflet.Map;
  private layer?: Leaflet.LayerGroup;
  private resize?: ResizeObserver;
  private initialFit = false;
  private manuallyMoved = false;
  private centerSeen = 0;
  private selectedSeen = '';
  private readonly markers = new Map<string, { marker: Leaflet.Marker; latitude: number; longitude: number; popupKey: string; content: HTMLElement }>();
  private painting = false;
  constructor() {
    afterNextRender(() => { void this.initialize(); });
    effect(() => { this.units(); this.selectedId(); if (this.map) this.paint(); });
    effect(() => { const request = this.centerRequest(); if (this.map && request !== this.centerSeen) { this.centerSeen = request; this.center(); } });
  }
  private async initialize(): Promise<void> {
    try {
      const [lib] = await Promise.all([this.loadLeaflet(),this.loadStyles()]); if (this.destroyed.destroyed) return;
      this.leaflet = lib;
      this.map = lib.map(this.canvas().nativeElement, { zoomControl: true, attributionControl: false, zoomAnimation: false, markerZoomAnimation: false }).setView([-12.05, -77.04], 11);
      lib.tileLayer(this.tileUrl(), { minZoom: 3, maxZoom: 19, maxNativeZoom: 19, updateWhenIdle: true, keepBuffer: 0,
        crossOrigin: 'anonymous', referrerPolicy: 'strict-origin-when-cross-origin' }).on('tileerror', () => this.failed.set(true)).addTo(this.map);
      this.layer = lib.layerGroup().addTo(this.map);
      this.map.on('dragstart', () => { this.manuallyMoved = true; });
      this.map.on('zoomstart', () => { if (this.initialFit) this.manuallyMoved = true; });
      this.map.on('zoomend moveend', () => this.paint());
      if (typeof ResizeObserver !== 'undefined') { this.resize = new ResizeObserver(() => this.map?.invalidateSize({ pan: false })); this.resize.observe(this.canvas().nativeElement); }
      this.paint();
    } catch { if (!this.destroyed.destroyed) this.failed.set(true); }
  }
  private paint(): void {
    const lib = this.leaflet, map = this.map;
    if (!lib || !map || !this.layer || this.painting) return;
    this.painting = true;
    const units = this.units();
    const retained = new Set<string>();
    try {
    if (units.length && !this.initialFit && !this.manuallyMoved) { this.initialFit = true; this.fitAll(); }
    for (const cluster of clusterLiveMapUnits(units, row => map.latLngToContainerPoint([row.latitude, row.longitude]), this.selectedId())) {
      const first = cluster[0];
      const key = cluster.length > 1 ? 'cluster:' + cluster.map(row => row.driverId).sort().join('|') : 'unit:' + first.driverId;
      retained.add(key);
      let entry = this.markers.get(key);
      if (cluster.length > 1) {
        if (!entry) {
        const content = document.createElement('span'); content.className = 'rf-live-map-marker__cluster'; content.textContent = String(cluster.length);
        const marker = lib.marker([first.latitude, first.longitude], { icon: lib.divIcon({ html: content, className: 'rf-live-map-marker', iconSize: [42, 42], iconAnchor: [21, 21] }),
          title: `${cluster.length} unidades agrupadas; acercar mapa`, keyboard: true }).addTo(this.layer);
        const ids = new Set(cluster.map(row => row.driverId));
        marker.on('click', () => {
          const current = this.units().filter(row => ids.has(row.driverId));
          if (current.length) { this.manuallyMoved = true; map.fitBounds(lib.latLngBounds(current.map(row => [row.latitude, row.longitude])), { padding: [45, 45], maxZoom: Math.min(19, map.getZoom() + 2), animate: false }); }
        });
        entry = { marker, latitude: first.latitude, longitude: first.longitude, popupKey: '', content }; this.markers.set(key, entry);
        }
      } else {
        if (!entry) {
        const content = document.createElement('span'); content.className = `rf-live-map-marker__unit${first.freshness === 'STALE' ? ' rf-live-map-marker__unit--stale' : ''}`;
        const icon = this.carIcon();
        const label = document.createElement('span'); label.className = 'rf-live-map-marker__plate'; label.textContent = first.plate; content.append(icon, label);
        const marker = lib.marker([first.latitude, first.longitude], { icon: lib.divIcon({ html: content, className: 'rf-live-map-marker', iconSize: [42, 42], iconAnchor: [21, 21] }),
          title: `${first.plate}, ${first.driverName}, ${first.freshness === 'CURRENT' ? 'ubicación vigente' : 'ubicación desactualizada'}`, keyboard: true }).addTo(this.layer);
        marker.bindPopup(this.popup(first), { autoPan: false }); marker.on('click', () => this.selectUnit.emit(first.driverId));
        entry = { marker, latitude: first.latitude, longitude: first.longitude, popupKey: '', content }; this.markers.set(key, entry);
        }
        const popupKey = JSON.stringify([first.plate, first.driverName, first.capturedAt, first.freshness]);
        if (entry.popupKey !== popupKey) {
          entry.content.classList.toggle('rf-live-map-marker__unit--stale', first.freshness === 'STALE');
          const plate = entry.content.querySelector('.rf-live-map-marker__plate'); if (plate) plate.textContent = first.plate;
          const title = `${first.plate}, ${first.driverName}, ${first.freshness === 'CURRENT' ? 'ubicación vigente' : 'ubicación desactualizada'}`;
          entry.marker.getElement()?.setAttribute('title', title); entry.marker.getElement()?.setAttribute('aria-label', title);
          entry.marker.setPopupContent(this.popup(first)); entry.popupKey = popupKey;
        }
      }
      if (entry.latitude !== first.latitude || entry.longitude !== first.longitude) {
        entry.marker.setLatLng([first.latitude, first.longitude]); entry.latitude = first.latitude; entry.longitude = first.longitude;
      }
    }
    for (const [key, entry] of this.markers) {
      if (retained.has(key)) continue;
      const focused = entry.marker.getElement()?.contains(document.activeElement) ?? false;
      this.layer.removeLayer(entry.marker); this.markers.delete(key);
      if (focused) this.canvas().nativeElement.focus({ preventScroll: true });
    }
    const selected = this.selectedId();
    const priorSelected = this.selectedSeen; this.selectedSeen = selected;
    if (selected) {
      if (selected !== priorSelected) this.markers.get('unit:' + selected)?.marker.openPopup();
      if (selected !== priorSelected && !this.manuallyMoved) { const row = units.find(unit => unit.driverId === selected); if (row) map.panTo([row.latitude, row.longitude], { animate: false }); }
    }
    } finally { this.painting = false; }
  }
  private carIcon(): SVGElement {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('width', '23'); svg.setAttribute('height', '23'); svg.setAttribute('aria-hidden', 'true');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', 'm5 10 1.5-5h11l1.5 5M4 10h16v8H4zM6 18v2m12-2v2M7 14h2m6 0h2');
    path.setAttribute('fill', 'none'); path.setAttribute('stroke', 'currentColor'); path.setAttribute('stroke-width', '1.8'); path.setAttribute('stroke-linecap', 'round'); path.setAttribute('stroke-linejoin', 'round'); svg.append(path); return svg;
  }
  private popup(row: LiveMapUnit): HTMLElement {
    const box = document.createElement('div'); box.className = 'rf-live-map-popup';
    for (const [tag, text] of [['strong', row.plate], ['span', row.driverName], ['span', row.freshness === 'CURRENT' ? 'Ubicación vigente' : 'Ubicación desactualizada'],
      ['span', `Capturada: ${new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'America/Lima' }).format(new Date(row.capturedAt))}`]]) {
      const item = document.createElement(tag); item.textContent = text; box.append(item);
    }
    return box;
  }
  private center(): void {
    const selected = this.units().find(row => row.driverId === this.selectedId());
    if (selected) this.map?.setView([selected.latitude, selected.longitude], Math.max(this.map.getZoom(), 14), { animate: false }); else this.fitAll();
  }
  private fitAll(): void { if (this.units().length && this.map && this.leaflet) this.map.fitBounds(this.leaflet.latLngBounds(this.units().map(row => [row.latitude, row.longitude])), { padding: [45, 45], maxZoom: 15, animate: false }); }
  ngOnDestroy(): void { this.resize?.disconnect(); this.map?.off(); this.map?.remove(); this.map = undefined; this.layer = undefined; this.markers.clear(); }
}
