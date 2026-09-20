import { existsSync } from 'node:fs';
import { loadEnvFile } from 'node:process';
import { resolve } from 'node:path';

const localEnvironment = resolve(process.cwd(), '.env');
if (existsSync(localEnvironment)) loadEnvFile(localEnvironment);

type ServerConfig = {
  production: boolean;
  port: number;
  dbHost: string;
  dbPort: number;
  dbName: string;
  dbUser: string;
  dbPassword: string;
  allowedOrigins: ReadonlySet<string>;
  trustProxyHops: number;
};

let cached: ServerConfig | undefined;

export function serverConfig(): ServerConfig {
  if (cached) return cached;
  const production = process.env.NODE_ENV === 'production';
  if (production) {
    const missing = ['PORT', 'DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD', 'ALLOWED_ORIGINS']
      .filter(name => !process.env[name]?.trim());
    if (missing.length) throw new Error(`Missing required production settings: ${missing.join(', ')}`);
  }
  const port = integerSetting('PORT', 3000, 1, 65_535);
  const dbPort = integerSetting('DB_PORT', 5432, 1, 65_535);
  const trustProxyHops = integerSetting('TRUST_PROXY_HOPS', 0, 0, 10);
  const originValues = (process.env.ALLOWED_ORIGINS ?? 'http://localhost:3000,http://127.0.0.1:3000')
    .split(',').map(value => value.trim()).filter(Boolean);
  const allowedOrigins = new Set(originValues.map(value => {
    if (value === '*') throw new Error('ALLOWED_ORIGINS cannot contain a wildcard');
    const parsed = new URL(value);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') throw new Error('ALLOWED_ORIGINS must use HTTP or HTTPS origins');
    return parsed.origin;
  }));
  cached = {
    production,
    port,
    dbHost: process.env.DB_HOST ?? 'localhost',
    dbPort,
    dbName: process.env.DB_NAME ?? 'mathfight',
    dbUser: process.env.DB_USER ?? 'mathfight',
    dbPassword: process.env.DB_PASSWORD ?? 'mathfight',
    allowedOrigins,
    trustProxyHops,
  };
  return cached;
}

export function allowOrigin(origin: string | undefined) {
  return origin === undefined || serverConfig().allowedOrigins.has(origin);
}

function integerSetting(name: string, fallback: number, minimum: number, maximum: number) {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value < minimum || value > maximum) throw new Error(`${name} is invalid`);
  return value;
}
