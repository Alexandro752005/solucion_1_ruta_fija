import { LiveMapUnit } from '../../core/live-map/live-map.models';

export interface MapPixel { readonly x: number; readonly y: number; }
/** Small bounded pixel clusters. The marker anchor is an existing point, never an invented position. */
export function clusterLiveMapUnits(items: readonly LiveMapUnit[], project: (unit: LiveMapUnit) => MapPixel, selectedId = '', radius = 44): readonly (readonly LiveMapUnit[])[] {
  const clusters: LiveMapUnit[][] = [];
  for (const item of items.slice(0, 20)) {
    if (item.driverId === selectedId) { clusters.push([item]); continue; }
    const p = project(item);
    const cluster = clusters.find(group => group[0].driverId !== selectedId && group.some(row => {
      const other = project(row); return Math.hypot(p.x - other.x, p.y - other.y) < radius;
    }));
    if (cluster) cluster.push(item); else clusters.push([item]);
  }
  return clusters;
}
