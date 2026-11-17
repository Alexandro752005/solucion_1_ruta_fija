import { Component, input, output, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LiveMapCartographyService } from '../../core/live-map/live-map-cartography.service';
import { LiveMapUnit } from '../../core/live-map/live-map.models';
import { LiveMapCanvasComponent } from './live-map-canvas.component';
import { LiveMapFacade } from './live-map.facade';
import { LiveMapPage } from './live-map.page';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({selector:'rf-live-map-canvas',template:'<p>Mapa sintético sin teselas externas</p>'})
class CanvasDouble { readonly units=input<readonly LiveMapUnit[]>([]); readonly selectedId=input('');readonly centerRequest=input(0);readonly tileUrl=input('');readonly selectUnit=output<string>(); }
const unit:LiveMapUnit={driverId:'11111111-1111-4111-8111-111111111111',vehicleId:'22222222-2222-4222-8222-222222222222',driverName:'<img src=x onerror=alert(1)>',plate:'TEST-01',latitude:-12,longitude:-77,capturedAt:'2026-11-11T12:00:00Z',receivedAt:'2026-11-11T12:00:01Z',expiresAt:'2026-11-11T12:05:01Z',freshness:'CURRENT'};
describe('live map main presentation without remote requests',()=>{
  const data={start:vi.fn(),stop:vi.fn(),refresh:vi.fn(),setGroup:vi.fn(),setName:vi.fn(),setLimit:vi.fn(),clearFilters:vi.fn(),select:vi.fn(),
    units:signal<readonly LiveMapUnit[]>([unit]),groups:signal([]),state:signal('ready'),busy:signal(false),suspended:signal(false),notice:signal(''),limit:signal(5),groupId:signal(''),driverName:signal(''),selectedId:signal(''),organization:signal('Empresa sintética'),connection:signal('connected'),statusText:()=> 'En línea',limits:[5,10,15,20]};
  const cartography={load:vi.fn(),stop:vi.fn(),tileUrl:signal<string|null>('https://tiles.example.invalid/{z}/{x}/{y}.png'),unavailable:signal(false)};
  beforeEach(async()=>{
    vi.clearAllMocks();data.units.set([unit]);data.state.set('ready');data.suspended.set(false);cartography.tileUrl.set('https://tiles.example.invalid/{z}/{x}/{y}.png');
    await TestBed.configureTestingModule({imports:[LiveMapPage],providers:[provideRouter([])]}).overrideComponent(LiveMapPage,{set:{imports:[FormsModule,DatePipe,RouterLink,CanvasDouble],providers:[{provide:LiveMapFacade,useValue:data},{provide:LiveMapCartographyService,useValue:cartography}]}}).compileComponents();
  });
  afterEach(()=>TestBed.resetTestingModule());
  it('renders names as text, never coordinates, and retains a keyboard accessible alternative',()=>{
    const fixture=TestBed.createComponent(LiveMapPage);fixture.detectChanges();const root=fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h1')?.textContent).toBe('Mapa en vivo');expect(root.querySelector('.unit-identity')?.textContent).toContain(unit.driverName);
    expect(root.querySelector('img')).toBeNull();expect(root.textContent).not.toContain('-77');expect(root.querySelector('.unit-select')?.getAttribute('aria-pressed')).toBe('false');
    root.querySelector<HTMLButtonElement>('.unit-select')!.click();expect(data.select).toHaveBeenCalledWith(unit.driverId);
    expect(root.querySelector('.unit-actions a')?.getAttribute('href')).toContain('vehicleId='+unit.vehicleId);
    expect(root.querySelector('form')?.getAttribute('aria-label')).toBe('Filtros del mapa en vivo');
  });
  it('removes map and cards for denied or paused views and keeps the explanation',()=>{
    const fixture=TestBed.createComponent(LiveMapPage);fixture.detectChanges();data.suspended.set(true);data.state.set('forbidden');fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('rf-live-map-canvas')).toBeNull();expect(fixture.nativeElement.querySelector('.unit-list')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Consulta de ubicación restringida');
  });
  it('preserves the list and explanation if configuration or tiles cannot be loaded',()=>{
    cartography.tileUrl.set(null);cartography.unavailable.set(true);const fixture=TestBed.createComponent(LiveMapPage);fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.unit-list')).not.toBeNull();expect(fixture.nativeElement.querySelector('.map-placeholder')?.textContent).toContain('no está disponible');
    expect(fixture.nativeElement.textContent).toContain('proveedor puede conocer tu IP');
    fixture.destroy();expect(data.stop).toHaveBeenCalledTimes(1);expect(cartography.stop).toHaveBeenCalledTimes(1);
  });
});
