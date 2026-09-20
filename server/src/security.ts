import { Injectable, Logger, OnApplicationShutdown } from '@nestjs/common';

export type SecurityCode =
  | 'INVALID_PAYLOAD'
  | 'RATE_LIMITED'
  | 'AUTH_REQUIRED'
  | 'SESSION_EXPIRED'
  | 'SERVICE_UNAVAILABLE'
  | 'SERVER_SHUTDOWN'
  | 'PROFILE_REQUIRED'
  | 'ACCOUNT_IN_USE'
  | 'ALREADY_IN_ROOM'
  | 'ROOM_NOT_FOUND'
  | 'ROOM_FULL'
  | 'INVALID_ROOM_CODE'
  | 'FORBIDDEN'
  | 'INVALID_STATE'
  | 'MATCH_UNAVAILABLE'
  | 'STALE_REQUEST'
  | 'NOT_OPEN'
  | 'QUESTION_EXPIRED'
  | 'ALREADY_RESOLVED'
  | 'INCORRECT_ANSWER';

const SAFE_MESSAGES: Record<SecurityCode, string> = {
  INVALID_PAYLOAD: 'Invalid request.',
  RATE_LIMITED: 'Too many attempts. Try again shortly.',
  AUTH_REQUIRED: 'Session expired. Reconnect.',
  SESSION_EXPIRED: 'Session expired. Reconnect.',
  SERVICE_UNAVAILABLE: 'Server temporarily unavailable.',
  SERVER_SHUTDOWN: 'Server temporarily unavailable.',
  PROFILE_REQUIRED: 'Complete your profile first.',
  ACCOUNT_IN_USE: 'This account is already connected.',
  ALREADY_IN_ROOM: 'You are already in a room or match.',
  ROOM_NOT_FOUND: 'Room not found.',
  ROOM_FULL: 'Room is full.',
  INVALID_ROOM_CODE: 'Invalid room code.',
  FORBIDDEN: 'Action is not allowed.',
  INVALID_STATE: 'Action is not available now.',
  MATCH_UNAVAILABLE: 'Match unavailable.',
  STALE_REQUEST: 'Request is no longer current.',
  NOT_OPEN: 'Question is not open yet.',
  QUESTION_EXPIRED: 'Question expired.',
  ALREADY_RESOLVED: 'Question already resolved.',
  INCORRECT_ANSWER: 'Try again.',
};

export function safeError(code: SecurityCode, retryAfterMs = 1_000, extra: Record<string, unknown> = {}) {
  return { ...extra, ok: false, code, message: SAFE_MESSAGES[code], retryAfterMs: Math.max(0, Math.ceil(retryAfterMs)) };
}

export function isStrictPayload(value: unknown, required: readonly string[] = [], optional: readonly string[] = []): value is Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value) || Buffer.isBuffer(value)) return false;
  const prototype = Object.getPrototypeOf(value);
  if (prototype !== Object.prototype && prototype !== null) return false;
  const keys = Object.keys(value);
  const allowed = new Set([...required, ...optional]);
  if (keys.some(key => !allowed.has(key)) || required.some(key => !Object.prototype.hasOwnProperty.call(value, key))) return false;
  try {
    return Buffer.byteLength(JSON.stringify(value), 'utf8') <= 16 * 1024;
  } catch {
    return false;
  }
}

export const isUuid = (value: unknown): value is string => typeof value === 'string' &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
export const isUuidV4 = (value: unknown): value is string => typeof value === 'string' &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
export const isSecretToken = (value: unknown): value is string => typeof value === 'string' && /^[0-9a-f]{64}$/i.test(value);
export const isPositiveSafeInteger = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
export const isRevision = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
export const isAnswer = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && Number.isInteger(value) && value >= 0 && value <= 999;

export function normalizePlayerName(value: unknown) {
  if (typeof value !== 'string' || value.length > 64) return undefined;
  const name = value.replace(/^ +| +$/g, '').replace(/ +/g, ' ');
  const length = Array.from(name).length;
  return length >= 3 && length <= 16 && !/[^\p{L}\p{N}_ ]/u.test(name) ? name : undefined;
}

type LimitRule = { scope: string; limit: number; windowMs: number };
type LimitRecord = { timestamps: number[]; lastSeen: number; windowMs: number; lastWarningAt: number };
export type LimitResult = { allowed: true } | { allowed: false; retryAfterMs: number; shouldLog: boolean };

@Injectable()
export class SecurityService implements OnApplicationShutdown {
  private readonly logger = new Logger('Security');
  private readonly limits = new Map<string, LimitRecord>();
  private readonly cleanupTimer = setInterval(() => this.prune(), 5 * 60_000).unref();
  private acceptingWork = true;

  isAcceptingWork() { return this.acceptingWork; }
  beginShutdown() { this.acceptingWork = false; }

  consume(subject: string, rules: readonly LimitRule[]): LimitResult {
    const now = Date.now();
    const entries = rules.map(rule => {
      const key = `${rule.scope}:${subject}`;
      let record = this.limits.get(key);
      if (!record) {
        this.evictIfNeeded();
        record = { timestamps: [], lastSeen: now, windowMs: rule.windowMs, lastWarningAt: 0 };
        this.limits.set(key, record);
      }
      record.timestamps = record.timestamps.filter(timestamp => timestamp > now - rule.windowMs);
      record.lastSeen = now;
      record.windowMs = Math.max(record.windowMs, rule.windowMs);
      return { rule, record };
    });
    const blocked = entries.filter(({ rule, record }) => record.timestamps.length >= rule.limit);
    if (blocked.length) {
      const retryAfterMs = Math.max(...blocked.map(({ rule, record }) => record.timestamps[0] + rule.windowMs - now), 1);
      const shouldLog = blocked.some(({ record }) => {
        if (now - record.lastWarningAt < 60_000) return false;
        record.lastWarningAt = now;
        return true;
      });
      return { allowed: false, retryAfterMs, shouldLog };
    }
    for (const { record } of entries) record.timestamps.push(now);
    return { allowed: true };
  }

  rateError(event: string, socketId: string, result: Exclude<LimitResult, { allowed: true }>) {
    if (result.shouldLog) this.logger.warn(JSON.stringify({ event, code: 'RATE_LIMITED', socketId }));
    return safeError('RATE_LIMITED', result.retryAfterMs);
  }

  securityWarning(event: string, code: SecurityCode, socketId: string) {
    const result = this.consume(socketId, [{ scope: `warning-${event}-${code}`, limit: 1, windowMs: 60_000 }]);
    if (result.allowed) this.logger.warn(JSON.stringify({ event, code, socketId }));
  }

  onApplicationShutdown() {
    clearInterval(this.cleanupTimer);
    this.limits.clear();
  }

  private prune() {
    const now = Date.now();
    for (const [key, record] of this.limits) {
      if (now - record.lastSeen > record.windowMs) this.limits.delete(key);
    }
  }

  private evictIfNeeded() {
    if (this.limits.size < 10_000) return;
    let oldestKey: string | undefined;
    let oldest = Number.POSITIVE_INFINITY;
    for (const [key, record] of this.limits) {
      if (record.lastSeen < oldest) { oldest = record.lastSeen; oldestKey = key; }
    }
    if (oldestKey) this.limits.delete(oldestKey);
  }
}
