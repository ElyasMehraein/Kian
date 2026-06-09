const BASE32 = '0123456789bcdefghjkmnpqrstuvwxyz';
const DEFAULT_PRECISION = 6;
const EARTH_RADIUS_KM = 6371;

interface GeohashBounds {
  lat: [number, number];
  lon: [number, number];
}

function decodeBounds(hash: string): GeohashBounds {
  let isEvenBit = true;
  let lat: [number, number] = [-90, 90];
  let lon: [number, number] = [-180, 180];

  for (const char of hash) {
    const value = BASE32.indexOf(char);

    if (value === -1) {
      throw new Error(`Invalid geohash character: ${char}`);
    }

    for (let mask = 16; mask > 0; mask >>= 1) {
      if (isEvenBit) {
        const midpoint = (lon[0] + lon[1]) / 2;
        lon = value & mask ? [midpoint, lon[1]] : [lon[0], midpoint];
      } else {
        const midpoint = (lat[0] + lat[1]) / 2;
        lat = value & mask ? [midpoint, lat[1]] : [lat[0], midpoint];
      }

      isEvenBit = !isEvenBit;
    }
  }

  return { lat, lon };
}

function clampLatitude(lat: number): number {
  return Math.max(-90, Math.min(90, lat));
}

function wrapLongitude(lon: number): number {
  if (lon < -180) {
    return lon + 360;
  }

  if (lon > 180) {
    return lon - 360;
  }

  return lon;
}

export function encodeGeohash(
  lat: number,
  lon: number,
  precision = DEFAULT_PRECISION,
): string {
  let isEvenBit = true;
  let bit = 0;
  let value = 0;
  let hash = '';
  let latRange: [number, number] = [-90, 90];
  let lonRange: [number, number] = [-180, 180];

  while (hash.length < precision) {
    if (isEvenBit) {
      const midpoint = (lonRange[0] + lonRange[1]) / 2;
      const upperHalf = lon >= midpoint;
      value = (value << 1) | Number(upperHalf);
      lonRange = upperHalf ? [midpoint, lonRange[1]] : [lonRange[0], midpoint];
    } else {
      const midpoint = (latRange[0] + latRange[1]) / 2;
      const upperHalf = lat >= midpoint;
      value = (value << 1) | Number(upperHalf);
      latRange = upperHalf ? [midpoint, latRange[1]] : [latRange[0], midpoint];
    }

    isEvenBit = !isEvenBit;
    bit += 1;

    if (bit === 5) {
      hash += BASE32[value];
      bit = 0;
      value = 0;
    }
  }

  return hash;
}

export function decodeGeohash(hash: string): { lat: number; lon: number } {
  const bounds = decodeBounds(hash);

  return {
    lat: (bounds.lat[0] + bounds.lat[1]) / 2,
    lon: (bounds.lon[0] + bounds.lon[1]) / 2,
  };
}

export function geohashDistance(hash1: string, hash2: string): number {
  if (hash1 === hash2) {
    return 0;
  }

  const point1 = decodeGeohash(hash1);
  const point2 = decodeGeohash(hash2);
  const lat1 = (point1.lat * Math.PI) / 180;
  const lat2 = (point2.lat * Math.PI) / 180;
  const deltaLat = ((point2.lat - point1.lat) * Math.PI) / 180;
  const deltaLon = ((point2.lon - point1.lon) * Math.PI) / 180;
  const haversine =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;

  return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
}

export function geohashNeighbors(hash: string): string[] {
  const bounds = decodeBounds(hash);
  const center = decodeGeohash(hash);
  const latStep = bounds.lat[1] - bounds.lat[0];
  const lonStep = bounds.lon[1] - bounds.lon[0];
  const neighbors: string[] = [];

  for (const latOffset of [-1, 0, 1]) {
    for (const lonOffset of [-1, 0, 1]) {
      if (latOffset === 0 && lonOffset === 0) {
        continue;
      }

      neighbors.push(
        encodeGeohash(
          clampLatitude(center.lat + latStep * latOffset),
          wrapLongitude(center.lon + lonStep * lonOffset),
          hash.length,
        ),
      );
    }
  }

  return neighbors;
}
