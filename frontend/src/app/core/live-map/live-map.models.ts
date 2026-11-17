export interface LiveMapUnit {
  readonly driverId: string; readonly vehicleId: string; readonly driverName: string; readonly plate: string;
  readonly latitude: number; readonly longitude: number;
  readonly capturedAt: string; readonly receivedAt: string; readonly expiresAt: string; readonly freshness: 'CURRENT' | 'STALE';
}
export interface LiveMapSnapshot { readonly serverTime: string; readonly limit: number; readonly items: readonly LiveMapUnit[]; }
export interface LiveMapGroup { readonly id: string; readonly name: string; }
export const LIVE_MAP_LIMITS = [5, 10, 15, 20] as const;
const record = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const text = (v: unknown, max: number): v is string => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const uuid = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(v);
const instant = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(v) && Number.isFinite(Date.parse(v));
const coordinate = (v: unknown, min: number, max: number): v is number => typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max;
export class InvalidLiveMapResponse extends Error { constructor() { super('La consulta del mapa no tiene un formato válido.'); } }
export function parseLiveMapSnapshot(value: unknown, limit: number): LiveMapSnapshot {
  if (!record(value) || !instant(value['serverTime']) || value['limit'] !== limit || !LIVE_MAP_LIMITS.includes(limit as 5)
    || !Array.isArray(value['items']) || value['items'].length > limit) throw new InvalidLiveMapResponse();
  const drivers = new Set<string>(), vehicles = new Set<string>();
  const items = value['items'].map((v: unknown): LiveMapUnit => {
    if (!record(v) || !uuid(v['driverId']) || !uuid(v['vehicleId']) || !text(v['driverName'], 250) || !text(v['plate'], 40)
      || !coordinate(v['latitude'], -90, 90) || !coordinate(v['longitude'], -180, 180)
      || !instant(v['capturedAt']) || !instant(v['receivedAt']) || !instant(v['expiresAt'])
      || Date.parse(v['expiresAt']) <= Math.max(Date.parse(v['capturedAt']), Date.parse(v['receivedAt']))
      || !['CURRENT', 'STALE'].includes(String(v['freshness'])) || drivers.has(v['driverId']) || vehicles.has(v['vehicleId'])) throw new InvalidLiveMapResponse();
    drivers.add(v['driverId']); vehicles.add(v['vehicleId']);
    return { driverId: v['driverId'], vehicleId: v['vehicleId'], driverName: v['driverName'], plate: v['plate'],
      latitude: v['latitude'], longitude: v['longitude'], capturedAt: v['capturedAt'], receivedAt: v['receivedAt'], expiresAt: v['expiresAt'],
      freshness: v['freshness'] as LiveMapUnit['freshness'] };
  });
  return { serverTime: value['serverTime'], limit, items };
}
export function parseLiveMapGroups(value: unknown): readonly LiveMapGroup[] {
  if (!record(value) || !Array.isArray(value['items']) || value['items'].length > 1000) throw new InvalidLiveMapResponse();
  const seen = new Set<string>();
  return value['items'].map((v: unknown) => {
    if (!record(v) || !uuid(v['id']) || !text(v['name'], 250) || seen.has(v['id'])) throw new InvalidLiveMapResponse();
    seen.add(v['id']); return { id: v['id'], name: v['name'] };
  });
}
export function visibleLiveMapUnits(items: readonly LiveMapUnit[], serverNow: number): readonly LiveMapUnit[] {
  return items.filter(row => Date.parse(row.expiresAt) > serverNow).map(row => ({ ...row,
    freshness: row.freshness === 'STALE' || serverNow - Date.parse(row.capturedAt) >= 120_000 ? 'STALE' : 'CURRENT',
  }));
}
/** XYZ only. No query, credentials, arbitrary template variables, or actor data reaches a tile host. */
export function validateTileUrl(value: unknown): string | null {
  if (typeof value !== 'string' || value.length > 500 || value !== value.trim() || !['{z}', '{x}', '{y}'].every(p => value.split(p).length === 2)) return null;
  const clean = value.replace(/\{[zxy]\}/g, '0');
  if (/[{}]/.test(clean)) return null;
  try {
    const url = new URL(clean);
    return url.protocol === 'https:' && !!url.hostname && !url.username && !url.password && !url.search && !url.hash
      && !/[\s\\]/.test(value) && !url.pathname.includes('%') ? value : null;
  } catch { return null; }
}
