import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { DashboardRouteViewerComponent } from './dashboard-route-viewer.component';
import { AdminDashboardViewModel } from './admin-dashboard.models';
const route = (): AdminDashboardViewModel['route'] => ({state:'ready',name:'SYNTHETIC ROUTE',revision:1,points:[{key:'a',label:'A',x:24,y:120},{key:'b',label:'B',x:316,y:30}]});
afterEach(()=>TestBed.resetTestingModule());
const setup=()=>{const fixture=TestBed.createComponent(DashboardRouteViewerComponent);fixture.componentRef.setInput('route',route());fixture.detectChanges();return fixture;};
describe('Registered route viewer keyboard controls',()=>{
  it('supports zoom, bounded pan and reset without modifying the registered points',()=>{
    const fixture=setup(),component=fixture.componentInstance;const original=JSON.stringify(component.route());
    for(let i=0;i<10;i++)component.zoomBy(1);expect(component.zoom()).toBe(4);for(let i=0;i<100;i++)component.pan(1,1);
    expect(component.x()).toBe(component.maxX());expect(component.y()).toBe(component.maxY());
    component.reset();expect(component.viewBox()).toBe('0 0 340 150');expect(JSON.stringify(component.route())).toBe(original);fixture.destroy();
  });
  it('retains zoom during an unrelated refresh of the same geometry',()=>{
    const fixture=setup();fixture.componentInstance.zoomBy(1);fixture.componentRef.setInput('route',{...route(),state:'refreshing'});fixture.detectChanges();expect(fixture.componentInstance.zoom()).toBe(2);fixture.destroy();
  });
  it('resets the transform for a different registered revision',()=>{
    const fixture=setup();fixture.componentInstance.zoomBy(1);fixture.componentRef.setInput('route',{...route(),revision:2});fixture.detectChanges();expect(fixture.componentInstance.zoom()).toBe(1);fixture.destroy();
  });
  it('preserves disconnected segments and provides a textual stop alternative',()=>{
    const fixture=setup(),value=route();fixture.componentRef.setInput('route',{...value,segments:[]});fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('polyline')).toBeNull();expect(fixture.nativeElement.textContent).toContain('coordenadas adyacentes suficientes');expect(fixture.nativeElement.querySelectorAll('ol li')).toHaveLength(2);fixture.destroy();
  });
});
