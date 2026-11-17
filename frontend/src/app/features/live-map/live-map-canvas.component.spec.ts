import { TestBed } from '@angular/core/testing';
import * as Leaflet from 'leaflet';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LIVE_MAP_LEAFLET, LIVE_MAP_STYLES, LiveMapCanvasComponent } from './live-map-canvas.component';
import { LiveMapUnit } from '../../core/live-map/live-map.models';

const unit:LiveMapUnit={driverId:'11111111-1111-4111-8111-111111111111',vehicleId:'22222222-2222-4222-8222-222222222222',driverName:'<img src=x onerror=alert(1)>',plate:'TEST-01',latitude:-12,longitude:-77,capturedAt:'2026-11-11T12:00:00Z',receivedAt:'2026-11-11T12:00:01Z',expiresAt:'2026-11-11T12:05:01Z',freshness:'CURRENT'};
describe('real Leaflet marker lifecycle with tiles replaced locally',()=>{
  let tile:Leaflet.LayerGroup, actualMap:Leaflet.Map;
  beforeEach(async()=>{
    tile=Leaflet.layerGroup();
    vi.spyOn(HTMLElement.prototype,'clientWidth','get').mockReturnValue(700);vi.spyOn(HTMLElement.prototype,'clientHeight','get').mockReturnValue(500);
    const loader=async()=>({...Leaflet,map:(container:string|HTMLElement,options:Leaflet.MapOptions)=>{actualMap=Leaflet.map(container,options);return actualMap;},tileLayer:()=>tile}) as unknown as typeof Leaflet;
    await TestBed.configureTestingModule({imports:[LiveMapCanvasComponent],providers:[{provide:LIVE_MAP_LEAFLET,useValue:loader},{provide:LIVE_MAP_STYLES,useValue:()=>Promise.resolve()}]}).compileComponents();
  });
  afterEach(()=>{TestBed.resetTestingModule();vi.restoreAllMocks();});
  async function setup(){
    const fixture=TestBed.createComponent(LiveMapCanvasComponent);fixture.componentRef.setInput('units',[unit]);fixture.componentRef.setInput('tileUrl','https://tiles.example.invalid/{z}/{x}/{y}.png');
    fixture.detectChanges();await fixture.whenStable();await Promise.resolve();fixture.detectChanges();return fixture;
  }
  it('retains the same focused marker through unchanged responses and real position changes',async()=>{
    const fixture=await setup();const root=fixture.nativeElement as HTMLElement;
    const marker=root.querySelector<HTMLElement>('.leaflet-marker-icon')!;expect(marker).not.toBeNull();expect(marker.querySelector('svg')).not.toBeNull();marker.focus();
    fixture.componentRef.setInput('units',[{...unit}]);fixture.detectChanges();expect(root.querySelector('.leaflet-marker-icon')).toBe(marker);expect(document.activeElement).toBe(marker);
    fixture.componentRef.setInput('units',[{...unit,latitude:-12.001}]);fixture.detectChanges();expect(root.querySelector('.leaflet-marker-icon')).toBe(marker);expect(document.activeElement).toBe(marker);
    fixture.componentRef.setInput('selectedId',unit.driverId);fixture.detectChanges();expect(root.querySelector('.leaflet-popup-content')?.textContent).toContain(unit.driverName);expect(root.querySelector('.leaflet-popup-content img')).toBeNull();
    fixture.destroy();expect(root.querySelectorAll('.leaflet-marker-icon')).toHaveLength(0);
  });
  it('withdraws removed units and shows tile errors while keeping accessible attribution',async()=>{
    const fixture=await setup();tile.fire('tileerror');fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('cartografía no está disponible');
    expect(fixture.nativeElement.querySelector('a[href="https://www.openstreetmap.org/copyright"]')).not.toBeNull();
    fixture.componentRef.setInput('units',[]);fixture.detectChanges();expect(fixture.nativeElement.querySelector('.leaflet-marker-icon')).toBeNull();
  });
  it('does not recenter a manually moved viewport when an invalidation is reconciled',async()=>{
    const fixture=await setup();fixture.componentRef.setInput('selectedId',unit.driverId);fixture.detectChanges();
    actualMap.fire('dragstart');const pan=vi.spyOn(actualMap,'panTo');pan.mockClear();
    fixture.componentRef.setInput('units',[]);fixture.componentRef.setInput('selectedId','');fixture.detectChanges();
    fixture.componentRef.setInput('units',[{...unit,latitude:-12.001}]);fixture.componentRef.setInput('selectedId',unit.driverId);fixture.detectChanges();
    expect(pan).not.toHaveBeenCalled();
  });
});
