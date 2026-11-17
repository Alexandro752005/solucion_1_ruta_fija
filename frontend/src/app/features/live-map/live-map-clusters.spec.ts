import { describe, expect, it } from 'vitest';
import { clusterLiveMapUnits } from './live-map-clusters';
import { LiveMapUnit } from '../../core/live-map/live-map.models';
const unit=(id:string,latitude:number,longitude:number):LiveMapUnit=>({driverId:id,vehicleId:id,driverName:'Sintético',plate:id,latitude,longitude,capturedAt:'2026-11-11T12:00:00Z',receivedAt:'2026-11-11T12:00:01Z',expiresAt:'2026-11-11T12:05:01Z',freshness:'CURRENT'});
describe('bounded proximity clusters',()=>{
  it('groups by visible pixel proximity and preserves the exact original positions',()=>{
    const rows=[unit('one',0,0),unit('two',1,1),unit('three',100,100)];
    const clusters=clusterLiveMapUnits(rows,row=>({x:row.latitude,y:row.longitude}));
    expect(clusters.map(group=>group.length)).toEqual([2,1]);expect(clusters[0][0]).toBe(rows[0]);expect(clusters.flat()).toEqual(rows);
  });
  it('keeps the selected unit independently focusable without inventing a centroid',()=>{
    const rows=[unit('one',0,0),unit('two',1,1)];
    expect(clusterLiveMapUnits(rows,row=>({x:row.latitude,y:row.longitude}),'two').map(group=>group.length)).toEqual([1,1]);
  });
  it('never processes more than twenty units or creates points for an empty feed',()=>{
    expect(clusterLiveMapUnits([],()=>({x:0,y:0}))).toEqual([]);
    expect(clusterLiveMapUnits(Array.from({length:30},(_,i)=>unit(String(i),i,i)),()=>({x:0,y:0})).flat()).toHaveLength(20);
  });
});
