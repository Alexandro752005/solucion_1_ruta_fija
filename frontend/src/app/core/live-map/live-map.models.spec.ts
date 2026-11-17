import { describe, expect, it } from 'vitest';
import { parseLiveMapGroups, parseLiveMapSnapshot, validateTileUrl, visibleLiveMapUnits } from './live-map.models';

export const syntheticUnit = {
  driverId: '11111111-1111-4111-8111-111111111111', vehicleId: '22222222-2222-4222-8222-222222222222',
  driverName: 'Conductor sintético', plate: 'TEST-01', latitude: -12.04, longitude: -77.03,
  capturedAt: '2026-11-11T12:00:00Z', receivedAt: '2026-11-11T12:00:01Z', expiresAt: '2026-11-11T12:05:01Z', freshness: 'CURRENT' as const,
};
const feed = () => ({ serverTime: '2026-11-11T12:00:10Z', limit: 5, items: [{ ...syntheticUnit }] });
describe('live map projection and tile boundary', () => {
  it('accepts only the minimal valid projection and the requested limit', () => {
    expect(parseLiveMapSnapshot(feed(), 5).items).toHaveLength(1);
    expect(parseLiveMapSnapshot({ ...feed(), items: [] }, 5).items).toEqual([]);
    expect(() => parseLiveMapSnapshot(feed(), 10)).toThrow();
  });
  it.each([{ latitude: 91 }, { longitude: NaN }, { driverId: 'bad-id' }, { expiresAt: 'invalid' }, { freshness: 'UNKNOWN' }, { driverName: '' }])('rejects unsafe or malformed telemetry %j', change => {
    expect(() => parseLiveMapSnapshot({ ...feed(), items: [{ ...syntheticUnit, ...change }] }, 5)).toThrow();
  });
  it('rejects oversized, duplicated or misordered-in-time payloads', () => {
    expect(() => parseLiveMapSnapshot({ ...feed(), items: [syntheticUnit, syntheticUnit] }, 5)).toThrow();
    expect(() => parseLiveMapSnapshot({ ...feed(), items: Array(6).fill(syntheticUnit) }, 5)).toThrow();
    expect(() => parseLiveMapSnapshot({ ...feed(), items: [{ ...syntheticUnit, expiresAt: syntheticUnit.receivedAt }] }, 5)).toThrow();
  });
  it('turns stale at 120 seconds and removes expiry without a new server response', () => {
    const at = Date.parse(syntheticUnit.capturedAt);
    expect(visibleLiveMapUnits([syntheticUnit], at + 119999)[0]?.freshness).toBe('CURRENT');
    expect(visibleLiveMapUnits([syntheticUnit], at + 120000)[0]?.freshness).toBe('STALE');
    expect(visibleLiveMapUnits([syntheticUnit], Date.parse(syntheticUnit.expiresAt))).toEqual([]);
  });
  it('accepts tenant group projection and rejects duplicate identifiers', () => {
    const group = { id: syntheticUnit.driverId, name: 'Grupo sintético' };
    expect(parseLiveMapGroups({ items: [group] })).toEqual([group]);
    expect(() => parseLiveMapGroups({ items: [group, group] })).toThrow();
  });
  it('allows a clean HTTPS XYZ template without credentials, query or personal placeholders', () => {
    expect(validateTileUrl('https://tile.openstreetmap.org/{z}/{x}/{y}.png')).toBe('https://tile.openstreetmap.org/{z}/{x}/{y}.png');
    for (const value of ['http://tile.test/{z}/{x}/{y}', 'https://a:b@tile.test/{z}/{x}/{y}', 'https://tile.test/{z}/{x}/{y}?token=secret', 'https://tile.test/{z}/{x}/{y}/{plate}', 'javascript:alert(1)', 'https://tile.test/x.png']) expect(validateTileUrl(value)).toBeNull();
  });
});
