import { ConnectedSocket, MessageBody, SubscribeMessage, WebSocketGateway, OnGatewayConnection, OnGatewayDisconnect, WebSocketServer } from '@nestjs/websockets';
import { BeforeApplicationShutdown, Logger, OnApplicationShutdown } from '@nestjs/common';
import { createHash, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { Server, Socket } from 'socket.io';
import { AccountService, InvalidAccountTokenError } from './account.service';
import { progressionResult } from './progression';
import { allowOrigin } from './server-config';
import { isAnswer, isPositiveSafeInteger, isRevision, isSecretToken, isStrictPayload, isUuid, isUuidV4, normalizePlayerName, safeError, SecurityService } from './security';

type Role = 'host' | 'guest';
type Difficulty = 'EASY' | 'STANDARD' | 'EXPERT';
type Question = { matchId: string; questionId: number; left: number; operation: 'ADD' | 'SUBTRACT' | 'MULTIPLY' | 'DIVIDE'; right: number };
type MatchPhase = 'SCHEDULED' | 'ANSWERING' | 'RESOLVING' | 'PAUSED' | 'FINISHED';
type ScheduleReason = 'FIRST' | 'NEXT' | 'RESUME';
type Match = { matchId: string; difficulty: Difficulty; ranked: boolean; hostName: string; guestName: string; question: Question; phase: MatchPhase; opensAt?: number; closesAt?: number; scheduleReason?: ScheduleReason; requiresReceipts: boolean; hostHp: number; guestHp: number; revision: number; attacker?: Role; winner?: Role; message?: string; startedAt: Date; timer?: NodeJS.Timeout; expiryTimer?: NodeJS.Timeout; requests: Map<string, object>; received: Set<string>; expiredQuestions: Set<number>; rating?: object; hostProgression?: ReturnType<typeof progressionResult>; guestProgression?: ReturnType<typeof progressionResult> };
type Player = { id: string; tokenHash: string; profileId?: string; displayName?: string; accountId?: string; rating?: number; searchId?: string; socket?: Socket; roomCode?: string; deadline?: number; graceTimer?: NodeJS.Timeout };
type Room = { code: string; difficulty: Difficulty; ranked: boolean; host: Player; guest?: Player; hostReady: boolean; guestReady: boolean; match?: Match };
type SearchEntry = { player: Player; searchId: string; difficulty: Difficulty; joinedAt: number };
const MATCHMAKING_SEARCH_TTL_MS = 5 * 60_000;
const COMBAT_ANIMATION_DURATION_MS = 860;
const NEXT_QUESTION_DELAY_MS = 1_000;

@WebSocketGateway({
  cors: { credentials: false, origin: (origin: string | undefined, callback: (error: Error | null, allowed?: boolean) => void) =>
    callback(null, allowOrigin(origin)) },
  allowRequest: (request: { headers: { origin?: string } }, callback: (error: string | null, success: boolean) => void) =>
    callback(null, allowOrigin(request.headers.origin)),
  maxHttpBufferSize: 16 * 1024,
  pingInterval: 3_000,
  pingTimeout: 5_000,
})
export class ConnectionGateway implements OnGatewayConnection, OnGatewayDisconnect, BeforeApplicationShutdown, OnApplicationShutdown {
  constructor(private readonly accounts: AccountService, private readonly security: SecurityService) {}
  @WebSocketServer() private server!: Server;
  private readonly logger = new Logger(ConnectionGateway.name);
  private readonly rooms = new Map<string, Room>();
  private readonly players = new Map<string, Player>();
  private readonly accountPlayers = new Map<string, Player>();
  private readonly profileSyncs = new Set<string>();
  private readonly matchmakingQueue: SearchEntry[] = [];
  private readonly pendingSettlements = new Set<Promise<unknown>>();
  private matchmakingTimer?: NodeJS.Timeout;
  private shuttingDown = false;

  handleConnection(client: Socket) {
    if (!this.security.isAcceptingWork()) {
      client.emit('server:shutdown', safeError('SERVER_SHUTDOWN'));
      client.disconnect(true);
      return;
    }
    const limit = this.security.consume(this.clientIp(client), [{ scope: 'connection', limit: 20, windowMs: 60_000 }]);
    if (!limit.allowed) {
      client.emit('server:error', this.security.rateError('connection', client.id, limit));
      client.disconnect(true);
      return;
    }
    client.data.acceptedConnection = true;
    this.logger.log(JSON.stringify({ event: 'socket_connected', socketId: client.id }));
  }

  handleDisconnect(client: Socket) {
    if (this.shuttingDown || !this.security.isAcceptingWork()) return;
    const player = this.playerFor(client);
    if (player) {
      if (this.removeSearch(player)) {
        player.socket = undefined;
        this.startGrace(player);
      } else this.connectionLost(player);
    }
    if (client.data.acceptedConnection === true) {
      this.logger.log(JSON.stringify({ event: 'socket_disconnected', socketId: client.id }));
    }
  }

  // Only the currently bound socket may act for this temporary session.
  private playerFor(client: Socket) {
    const id = typeof client.data.playerId === 'string' ? client.data.playerId : '';
    const player = this.players.get(id);
    return player?.socket === client ? player : undefined;
  }

  private authenticatedPlayer(client: Socket) {
    const player = this.playerFor(client);
    return player?.accountId && player.profileId && player.displayName ? player : undefined;
  }

  private clientIp(client: Socket) {
    return client.conn.remoteAddress || client.handshake.address || 'unknown';
  }

  private unavailable() {
    return this.security.isAcceptingWork() ? undefined : safeError('SERVER_SHUTDOWN');
  }

  private rate(client: Socket, subject: string, event: string, rules: readonly { scope: string; limit: number; windowMs: number }[]) {
    const result = this.security.consume(subject, rules);
    return result.allowed ? undefined : this.security.rateError(event, client.id, result);
  }

  private authError(client: Socket) {
    this.security.securityWarning('protected_event', 'AUTH_REQUIRED', client.id);
    return safeError('AUTH_REQUIRED');
  }

  @SubscribeMessage('session:open')
  openSession(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    if (!isStrictPayload(body, [], ['playerId', 'resumeToken'])) return safeError('INVALID_PAYLOAD');
    const hasPlayerId = Object.prototype.hasOwnProperty.call(body, 'playerId');
    const hasToken = Object.prototype.hasOwnProperty.call(body, 'resumeToken');
    if (hasPlayerId !== hasToken || (hasPlayerId && (!isUuid(body.playerId) || !isSecretToken(body.resumeToken)))) return safeError('INVALID_PAYLOAD');
    const limited = this.rate(client, this.clientIp(client), 'session_open', [{ scope: 'session-open', limit: 10, windowMs: 60_000 }]);
    if (limited) return limited;
    const bound = this.playerFor(client);
    if (bound) return this.sessionResponse(bound);
    let player: Player;
    let issuedToken: string | undefined;
    if (hasPlayerId && hasToken) {
      const existing = this.players.get(body.playerId as string);
      const suppliedHash = this.hashSecret(body.resumeToken as string);
      if (!existing || !timingSafeEqual(Buffer.from(suppliedHash, 'hex'), Buffer.from(existing.tokenHash, 'hex'))) return safeError('SESSION_EXPIRED');
      if (existing.deadline && Date.now() >= existing.deadline) {
        this.expirePlayer(existing);
        return safeError('SESSION_EXPIRED');
      }
      player = existing;
      if (player.socket && player.socket !== client) {
        const old = player.socket;
        if (this.removeSearch(player)) player.socket = undefined;
        else this.connectionLost(player);
        old.disconnect(true);
      }
    } else {
      issuedToken = randomBytes(32).toString('hex');
      player = { id: randomUUID(), tokenHash: this.hashSecret(issuedToken) };
      this.players.set(player.id, player);
    }
    clearTimeout(player.graceTimer);
    player.graceTimer = undefined;
    player.deadline = undefined;
    player.socket = client;
    client.data.playerId = player.id;
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    if (room?.match?.phase === 'PAUSED') this.resumeMatch(room);
    // Completed matches can be restored without replaying their presentation.
    if (room?.match?.phase === 'FINISHED' || room?.match?.phase === 'PAUSED') room.match.revision++;
    return this.sessionResponse(player, issuedToken);
  }

  private sessionResponse(player: Player, issuedToken?: string) {
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    return { ok: true, playerId: player.id, ...(issuedToken ? { resumeToken: issuedToken } : {}),
      room: room ? this.roomState(room, player) : undefined,
      snapshot: room?.match ? this.snapshot(room.match, room, room.host === player ? 'host' : 'guest') : undefined };
  }

  @SubscribeMessage('session:leave')
  leaveSession(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.playerFor(client);
    if (!player) return this.authError(client);
    this.removeSearch(player);
    this.leaveRoom(player);
    this.removePlayer(player);
    return { ok: true };
  }

  @SubscribeMessage('profile:sync')
  async syncProfile(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    if (!isStrictPayload(body, ['profileId', 'displayName'], ['accountToken']) || !isUuid(body.profileId)) return safeError('INVALID_PAYLOAD');
    const name = normalizePlayerName(body.displayName);
    const hasAccountToken = Object.prototype.hasOwnProperty.call(body, 'accountToken');
    if (!name || (hasAccountToken && !isSecretToken(body.accountToken))) return safeError('INVALID_PAYLOAD');
    const player = this.playerFor(client);
    if (!player) return this.authError(client);
    if (this.profileSyncs.has(player.id)) return safeError('INVALID_STATE');
    const authLimit = hasAccountToken
      ? this.rate(client, this.clientIp(client), 'account_authentication', [{ scope: 'account-auth', limit: 10, windowMs: 60_000 }])
      : this.rate(client, this.clientIp(client), 'account_registration', [{ scope: 'account-registration', limit: 5, windowMs: 60 * 60_000 }]);
    if (authLimit) return authLimit;
    const profileId = (body.profileId as string).toLowerCase();
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    if (room?.match && room.match.phase !== 'FINISHED' && (player.profileId !== profileId || player.displayName !== name)) return safeError('INVALID_STATE');
    this.profileSyncs.add(player.id);
    try {
      const account = await this.accounts.authenticate(profileId, name, hasAccountToken ? body.accountToken as string : undefined);
      if (player.accountId && player.accountId !== account.player.id) return safeError('FORBIDDEN');
      const active = this.accountPlayers.get(account.player.id);
      if (active && active !== player) {
        if (!active.socket && !active.roomCode && !active.searchId) this.removePlayer(active);
        else return safeError('ACCOUNT_IN_USE');
      }
      if (account.player.displayName !== name) {
        const nameLimit = this.rate(client, account.player.id, 'profile_name_change', [{ scope: 'profile-name', limit: 5, windowMs: 60 * 60_000 }]);
        if (nameLimit) return nameLimit;
      }
      const previouslyClaimed = this.accountPlayers.get(account.player.id) === player;
      this.accountPlayers.set(account.player.id, player);
      try {
        if (account.player.displayName !== name) await this.accounts.updateDisplayName(account.player.id, name);
      } catch {
        if (!previouslyClaimed) this.accountPlayers.delete(account.player.id);
        return safeError('SERVICE_UNAVAILABLE');
      }
      player.profileId = profileId;
      player.displayName = name;
      player.accountId = account.player.id;
      player.rating = account.player.rating;
      if (room) this.emitState(room.code);
      return { ok: true, displayName: name, ...(account.issuedToken ? { accountToken: account.issuedToken } : {}), room: room ? this.roomState(room, player) : undefined };
    } catch (error) {
      return error instanceof InvalidAccountTokenError ? safeError('AUTH_REQUIRED') : safeError('SERVICE_UNAVAILABLE');
    } finally {
      this.profileSyncs.delete(player.id);
    }
  }

  @SubscribeMessage('profile:stats')
  async profileStats(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'profile_stats', [{ scope: 'account-read', limit: 30, windowMs: 60_000 }]);
    if (limited) return limited;
    try {
      const stats = await this.accounts.stats(player.accountId!);
      return stats ? { ok: true, ...stats } : safeError('SERVICE_UNAVAILABLE');
    } catch {
      return safeError('SERVICE_UNAVAILABLE');
    }
  }

  @SubscribeMessage('leaderboard:get')
  async getLeaderboard(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'leaderboard_get', [{ scope: 'account-read', limit: 30, windowMs: 60_000 }]);
    if (limited) return limited;
    try {
      return { ok: true, ...(await this.accounts.leaderboard(player.accountId!)) };
    } catch {
      return safeError('SERVICE_UNAVAILABLE');
    }
  }
  private parseDifficulty(value: unknown): Difficulty | undefined {
    return value === 'EASY' || value === 'STANDARD' || value === 'EXPERT' ? value : undefined;
  }

  private tier(rating: number) { return rating < 900 ? 'Bronze' : rating < 1100 ? 'Silver' : rating < 1300 ? 'Gold' : rating < 1500 ? 'Platinum' : 'Diamond'; }

  @SubscribeMessage('connection:check')
  connectionCheck(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const limited = this.rate(client, client.id, 'connection_check', [{ scope: 'socket-check', limit: 20, windowMs: 60_000 }]);
    if (limited) return limited;
    return { ok: true, message: 'Math Fight server ready' };
  }

  @SubscribeMessage('time:sync')
  timeSync(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    if (!this.playerFor(client)) return this.authError(client);
    const limited = this.rate(client, client.id, 'time_sync', [{ scope: 'socket-time', limit: 12, windowMs: 60_000 }]);
    if (limited) return limited;
    return { ok: true, serverTime: Date.now() };
  }

  @SubscribeMessage('matchmaking:join')
  matchmakingJoin(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['difficulty'])) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const difficulty = this.parseDifficulty(body.difficulty);
    if (!difficulty) return safeError('INVALID_PAYLOAD');
    const limited = this.rate(client, player.accountId!, 'matchmaking_join', [{ scope: 'matchmaking', limit: 12, windowMs: 60_000 }]);
    if (limited) return limited;
    if (player.roomCode) return safeError('ALREADY_IN_ROOM');
    const existing = this.matchmakingQueue.find(entry => entry.player === player);
    if (existing) return { ok: true, status: 'waiting', searchId: existing.searchId, difficulty: existing.difficulty };
    const searchId = randomUUID();
    player.searchId = searchId;
    this.matchmakingQueue.push({ player, searchId, difficulty, joinedAt: Date.now() });
    this.startMatchmakingTimer();
    this.emitSearchStatus(player, searchId, 'waiting', difficulty);
    this.pairSearchers(difficulty);
    return { ok: true, status: 'waiting', searchId, difficulty };
  }

  @SubscribeMessage('matchmaking:cancel')
  matchmakingCancel(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['searchId']) || !isUuidV4(body.searchId)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'matchmaking_cancel', [{ scope: 'matchmaking', limit: 12, windowMs: 60_000 }]);
    if (limited) return limited;
    const entry = this.matchmakingQueue.find(item => item.player === player && item.searchId === body.searchId);
    if (!entry) return { ok: true, status: player.roomCode ? 'matched' : 'idle', searchId: body.searchId };
    this.removeSearch(player, entry.searchId);
    this.emitSearchStatus(player, entry.searchId, 'cancelled');
    return { ok: true, status: 'cancelled', searchId: entry.searchId };
  }

  @SubscribeMessage('matchmaking:status')
  matchmakingStatus(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'matchmaking_status', [{ scope: 'matchmaking-status', limit: 30, windowMs: 60_000 }]);
    if (limited) return limited;
    const entry = this.matchmakingQueue.find(item => item.player === player);
    return { ok: true, status: entry ? 'waiting' : player.roomCode ? 'matched' : 'idle', searchId: entry?.searchId };
  }

  private emitSearchStatus(player: Player, searchId: string, status: string, difficulty?: Difficulty) {
    player.socket?.emit('matchmaking:status', { searchId, status, difficulty });
  }

  private removeSearch(player: Player, searchId?: string) {
    const index = this.matchmakingQueue.findIndex(item => item.player === player && (!searchId || item.searchId === searchId));
    if (index < 0) return false;
    this.matchmakingQueue.splice(index, 1);
    player.searchId = undefined;
    if (!this.matchmakingQueue.length && this.matchmakingTimer) { clearInterval(this.matchmakingTimer); this.matchmakingTimer = undefined; }
    return true;
  }

  private pairSearchers(difficulty: Difficulty) {
    if (!this.security.isAcceptingWork()) return;
    this.discardInvalidSearchers();
    const matching = this.matchmakingQueue.filter(entry => entry.difficulty === difficulty);
    while (matching.length >= 2) {
      const first = matching.shift()!;
      const now = Date.now();
      const maxDifference = Math.min(300, Math.max(100, Math.floor((now - first.joinedAt) / 10_000) * 100 + 100));
      const secondIndex = matching.findIndex(entry => Math.abs((entry.player.rating ?? 1000) - (first.player.rating ?? 1000)) <= maxDifference || now - first.joinedAt >= 30_000);
      if (secondIndex < 0) break;
      const second = matching.splice(secondIndex, 1)[0];
      this.matchmakingQueue.splice(this.matchmakingQueue.indexOf(first), 1);
      this.matchmakingQueue.splice(this.matchmakingQueue.indexOf(second), 1);
      if (first.player === second.player || first.player.id === second.player.id) continue;
      first.player.searchId = undefined;
      second.player.searchId = undefined;
      this.createMatchedRoom(first, second);
    }
  }

  private startMatchmakingTimer() {
    if (this.matchmakingTimer || !this.security.isAcceptingWork()) return;
    this.matchmakingTimer = setInterval(() => {
      this.discardInvalidSearchers();
      for (const difficulty of ['EASY', 'STANDARD', 'EXPERT'] as Difficulty[]) this.pairSearchers(difficulty);
      if (!this.matchmakingQueue.length && this.matchmakingTimer) { clearInterval(this.matchmakingTimer); this.matchmakingTimer = undefined; }
    }, 1_000);
  }

  private discardInvalidSearchers() {
    const now = Date.now();
    for (let i = this.matchmakingQueue.length - 1; i >= 0; i--) {
      const entry = this.matchmakingQueue[i];
      const expired = now - entry.joinedAt >= MATCHMAKING_SEARCH_TTL_MS;
      if (expired || !entry.player.socket || !entry.player.displayName || entry.player.roomCode || entry.player.searchId !== entry.searchId) {
        this.matchmakingQueue.splice(i, 1);
        if (entry.player.searchId === entry.searchId) entry.player.searchId = undefined;
        if (expired) this.emitSearchStatus(entry.player, entry.searchId, 'expired', entry.difficulty);
      }
    }
  }

  private createMatchedRoom(first: SearchEntry, second: SearchEntry) {
    let host = first.player;
    let guest = second.player;
    if (Math.random() < 0.5) [host, guest] = [guest, host];
    let code = '';
    do { code = this.generateCode(); } while (this.rooms.has(code));
    const room: Room = { code, difficulty: first.difficulty, ranked: true, host, guest, hostReady: false, guestReady: false };
    this.rooms.set(code, room);
    host.roomCode = code;
    guest.roomCode = code;
    const hostState = this.roomState(room, host);
    const guestState = this.roomState(room, guest);
    const hostSearchId = host === first.player ? first.searchId : second.searchId;
    const guestSearchId = guest === first.player ? first.searchId : second.searchId;
    host.socket?.emit('matchmaking:matched', { searchId: hostSearchId, difficulty: room.difficulty, opponentName: guest.displayName, opponentRating: guest.rating ?? 1000, opponentTier: this.tier(guest.rating ?? 1000), role: 'host', room: hostState });
    guest.socket?.emit('matchmaking:matched', { searchId: guestSearchId, difficulty: room.difficulty, opponentName: host.displayName, opponentRating: host.rating ?? 1000, opponentTier: this.tier(host.rating ?? 1000), role: 'guest', room: guestState });
    this.emitState(code);
  }

  @SubscribeMessage('room:create')
  createRoom(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['difficulty'])) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const difficulty = this.parseDifficulty(body.difficulty);
    if (!difficulty) return safeError('INVALID_PAYLOAD');
    const limited = this.rate(client, player.accountId!, 'room_create', [{ scope: 'room-entry', limit: 10, windowMs: 60_000 }]);
    if (limited) return limited;
    if (player.roomCode || player.searchId) return safeError('ALREADY_IN_ROOM');
    let code = '';
    do { code = this.generateCode(); } while (this.rooms.has(code));
    this.rooms.set(code, { code, difficulty, ranked: false, host: player, hostReady: false, guestReady: false });
    player.roomCode = code;
    this.emitState(code);
    return { ok: true, room: this.roomState(this.rooms.get(code)!, player) };
  }

  @SubscribeMessage('room:join')
  joinRoom(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['code']) || typeof body.code !== 'string' || !/^[A-Z0-9]{6}$/.test(body.code)) return safeError('INVALID_ROOM_CODE');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'room_join', [{ scope: 'room-entry', limit: 10, windowMs: 60_000 }]);
    if (limited) return limited;
    if (player.roomCode || player.searchId) return safeError('ALREADY_IN_ROOM');
    const code = body.code;
    const room = this.rooms.get(code);
    if (!room) return safeError('ROOM_NOT_FOUND');
    if (room.guest) return safeError('ROOM_FULL');
    room.guest = player;
    player.roomCode = code;
    this.emitState(code);
    return { ok: true, room: this.roomState(room, player) };
  }

  @SubscribeMessage('room:leave')
  leaveRoomMessage(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    this.removeSearch(player);
    this.leaveRoom(player);
    return { ok: true };
  }

  @SubscribeMessage('room:ready')
  setReady(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['ready']) || typeof body.ready !== 'boolean') return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'room_ready', [{ scope: 'room-control', limit: 20, windowMs: 60_000 }]);
    if (limited) return limited;
    const code = player?.roomCode;
    const room = code ? this.rooms.get(code) : undefined;
    if (!room) return safeError('INVALID_STATE');
    if (room.match && room.match.phase !== 'FINISHED') return safeError('INVALID_STATE');
    if (room.ranked && room.match?.phase === 'FINISHED') return safeError('INVALID_STATE');
    const ready = body.ready;
    if (room.host === player) room.hostReady = ready;
    else if (room.guest === player) room.guestReady = ready;
    else return safeError('FORBIDDEN');
    this.emitState(room.code);
    if (room.host.socket && room.guest?.socket && room.hostReady && room.guestReady && (!room.match || room.match.phase === 'FINISHED')) this.startMatch(room);
    return { ok: true, ready };
  }

  @SubscribeMessage('room:difficulty')
  setDifficulty(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['difficulty'])) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'room_difficulty', [{ scope: 'room-control', limit: 20, windowMs: 60_000 }]);
    if (limited) return limited;
    const room = player?.roomCode ? this.rooms.get(player.roomCode) : undefined;
    const difficulty = this.parseDifficulty(body.difficulty);
    if (!difficulty) return safeError('INVALID_PAYLOAD');
    if (!room || room.host !== player || room.ranked) return safeError('FORBIDDEN');
    if (room.match && room.match.phase !== 'FINISHED') return safeError('INVALID_STATE');
    room.difficulty = difficulty;
    room.hostReady = false;
    room.guestReady = false;
    this.emitState(room.code);
    return { ok: true, difficulty };
  }

  @SubscribeMessage('match:answer')
  answer(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['requestId', 'matchId', 'questionId', 'answer']) ||
        !isUuidV4(body.requestId) || !isUuidV4(body.matchId) || !isPositiveSafeInteger(body.questionId) || !isAnswer(body.answer)) {
      return safeError('INVALID_PAYLOAD', 1_000, { result: 'invalid' });
    }
    const unavailable = this.unavailable();
    if (unavailable) return { ...unavailable, result: 'invalid', requestId: body.requestId, matchId: body.matchId, questionId: body.questionId };
    const player = this.authenticatedPlayer(client);
    if (!player) return { ...this.authError(client), result: 'invalid', requestId: body.requestId, matchId: body.matchId, questionId: body.questionId };
    const base = { requestId: body.requestId, matchId: body.matchId, questionId: body.questionId };
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    const match = room?.match;
    if (!room || !match) return safeError('MATCH_UNAVAILABLE', 1_000, { result: 'invalid', ...base });
    const role = room.host === player ? 'host' : room.guest === player ? 'guest' : undefined;
    if (!role) return safeError('FORBIDDEN', 1_000, { result: 'invalid', ...base });
    if (body.matchId !== match.matchId) return safeError('STALE_REQUEST', 1_000, { result: 'invalid', ...base });
    if (body.questionId !== match.question.questionId) {
      return match.expiredQuestions.has(body.questionId as number)
        ? safeError('QUESTION_EXPIRED', 1_000, { result: 'question_expired', ...base })
        : safeError('STALE_REQUEST', 1_000, { result: 'invalid', ...base });
    }
    const requestKey = player.id + ':' + body.requestId;
    const previous = match.requests.get(requestKey);
    if (previous) return previous;
    const limited = this.rate(client, player.accountId!, 'match_answer', [
      { scope: 'answer-burst', limit: 6, windowMs: 2_000 },
      { scope: 'answer-minute', limit: 60, windowMs: 60_000 },
    ]);
    if (limited) return { ...limited, result: 'rate_limited', ...base };
    if (match.phase === 'PAUSED' || match.phase === 'FINISHED') {
      const response = safeError('INVALID_STATE', 1_000, { result: 'invalid', ...base });
      this.rememberRequest(match, requestKey, response);
      return response;
    }
    if (match.phase === 'SCHEDULED' || match.opensAt === undefined || Date.now() < match.opensAt) {
      const response = safeError('NOT_OPEN', 1_000, { result: 'not_open', opensAt: match.opensAt, ...base });
      this.rememberRequest(match, requestKey, response);
      return response;
    }
    if (match.phase === 'ANSWERING' && match.closesAt !== undefined && Date.now() >= match.closesAt) {
      this.expireQuestion(room, match, match.question.questionId, match.revision);
      return safeError('QUESTION_EXPIRED', 1_000, { result: 'question_expired', ...base });
    }
    if (match.phase === 'ANSWERING' && match.closesAt === undefined) {
      return safeError('INVALID_STATE', 1_000, { result: 'invalid', ...base });
    }
    if (match.phase !== 'ANSWERING') {
      const result = match.phase === 'RESOLVING' ? 'already_resolved' : 'invalid';
      const response = safeError(match.phase === 'RESOLVING' ? 'ALREADY_RESOLVED' : 'INVALID_STATE', 1_000, { result, ...base });
      this.rememberRequest(match, requestKey, response);
      return response;
    }
    if (body.answer !== this.correctAnswer(match.question)) {
      const response = safeError('INCORRECT_ANSWER', 0, { result: 'incorrect', wrong: true, ...base });
      this.rememberRequest(match, requestKey, response);
      return response;
    }
    const accepted = { ok: true, result: 'correct', ...base };
    this.rememberRequest(match, requestKey, accepted);
    clearTimeout(match.expiryTimer);
    match.expiryTimer = undefined;
    match.closesAt = undefined;
    match.phase = 'RESOLVING';
    match.attacker = role;
    match.revision++;
    if (role === 'host') match.guestHp = Math.max(0, match.guestHp - 20);
    else match.hostHp = Math.max(0, match.hostHp - 20);
    const ko = match.hostHp === 0 || match.guestHp === 0;
    this.broadcastMatch(room, 'match:attack', { attacker: role, playerHp: match.hostHp, opponentHp: match.guestHp, matchId: match.matchId, questionId: match.question.questionId, ko, revision: match.revision });
    const questionId = match.question.questionId;
    match.timer = setTimeout(() => {
      const current = this.rooms.get(room.code);
      if (current !== room || current.match !== match || match.phase !== 'RESOLVING' || match.question.questionId !== questionId) return;
      if (ko) this.finishMatch(room, role);
      else this.scheduleQuestion(room, NEXT_QUESTION_DELAY_MS, 'NEXT', 'match:question');
    }, COMBAT_ANIMATION_DURATION_MS);
    return accepted;
  }
  @SubscribeMessage('match:state')
  matchState(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'match_state', [{ scope: 'match-state', limit: 30, windowMs: 60_000 }]);
    if (limited) return limited;
    const code = player?.roomCode;
    const room = code ? this.rooms.get(code) : undefined;
    if (!room?.match || (room.host !== player && room.guest !== player)) return safeError('MATCH_UNAVAILABLE', 1_000, { result: 'ended' });
    return { ok: true, snapshot: this.snapshot(room.match, room, room.host === player ? 'host' : 'guest') };
  }

  private leaveRoom(player: Player): string | undefined {
    const code = player.roomCode;
    if (!code) return undefined;
    player.roomCode = undefined;
    clearTimeout(player.graceTimer);
    player.deadline = undefined;
    const room = this.rooms.get(code);
    if (!room) return code;
    if (room.match && room.match.phase !== 'FINISHED') {
      const winner = room.match.hostHp === 0 ? 'guest' : room.match.guestHp === 0 ? 'host' : room.host === player ? 'guest' : 'host';
      this.finishMatch(room, winner, 'Opponent disconnected');
    }
    if (room.host === player) {
      this.rooms.delete(code);
      if (room.guest) {
        room.guest.roomCode = undefined;
        room.guest.socket?.emit('room:closed', { code, message: 'Host left the room' });
        if (!room.guest.socket) this.removePlayer(room.guest);
      }
    } else if (room.guest === player) {
      if (room.ranked) {
        this.rooms.delete(code);
        room.host.roomCode = undefined;
        room.host.socket?.emit('room:closed', { code, message: 'Opponent left the room' });
        if (!room.host.socket) this.removePlayer(room.host);
        return code;
      }
      room.guest = undefined;
      room.hostReady = false;
      room.guestReady = false;
      room.match = undefined;
      if (!room.host.socket) {
        // The remaining host cannot receive a result or own an abandoned lobby.
        this.rooms.delete(code);
        this.removePlayer(room.host);
      } else this.emitState(code);
    }
    return code;
  }

  private emitState(code: string) {
    const room = this.rooms.get(code);
    if (!room) return;
    room.host.socket?.emit('room:state', this.roomState(room, room.host));
    if (room.guest) room.guest.socket?.emit('room:state', this.roomState(room, room.guest));
  }

  private roomState(room: Room, player: Player) {
    return { code: room.code, role: room.host === player ? 'host' : 'guest', playerCount: room.guest ? 2 : 1,
      difficulty: room.difficulty, ranked: room.ranked, hostName: room.host.displayName, guestName: room.guest?.displayName,
      hostRating: room.host.rating ?? 1000, guestRating: room.guest?.rating, hostTier: this.tier(room.host.rating ?? 1000), guestTier: room.guest ? this.tier(room.guest.rating ?? 1000) : undefined,
      hostReady: room.hostReady, guestReady: room.guestReady, matchActive: Boolean(room.match && room.match.phase !== 'FINISHED') };
  }

  private startMatch(room: Room) {
    const matchId = randomUUID();
    const question = this.makeQuestion(matchId, 1, room.difficulty);
    room.match = { matchId, difficulty: room.difficulty, ranked: room.ranked, hostName: room.host.displayName!, guestName: room.guest!.displayName!, question, phase: 'SCHEDULED', requiresReceipts: false, hostHp: 100, guestHp: 100, revision: 0, requests: new Map(), received: new Set(), expiredQuestions: new Set(), startedAt: new Date() };
    this.emitState(room.code);
    this.scheduleQuestion(room, 3_000, 'FIRST', 'match:start', question);
  }

  private scheduleQuestion(room: Room, delayMs: number, reason: ScheduleReason, event: string, prepared?: Question) {
    const match = room.match;
    if (!match) return;
    clearTimeout(match.timer);
    clearTimeout(match.expiryTimer);
    match.expiryTimer = undefined;
    const previous = match.question;
    match.question = prepared ?? this.makeQuestion(match.matchId, previous.questionId + 1, match.difficulty, previous);
    match.phase = 'SCHEDULED';
    match.opensAt = Date.now() + delayMs;
    match.closesAt = match.opensAt + this.questionDuration(match.difficulty);
    match.scheduleReason = reason;
    match.requiresReceipts = reason === 'RESUME';
    match.attacker = undefined;
    match.requests.clear();
    match.received.clear();
    match.revision++;
    const scheduledRevision = match.revision;
    const payload = { ...match.question, difficulty: match.difficulty, ranked: match.ranked,
      matchType: match.ranked ? 'RANKED' : 'UNRANKED', hostName: match.hostName, guestName: match.guestName,
      playerHp: match.hostHp, opponentHp: match.guestHp, phase: match.phase, opensAt: match.opensAt, closesAt: match.closesAt,
      scheduleReason: reason, revision: match.revision };
    this.broadcastMatch(room, event, event === 'match:resumed' ? this.snapshot(match, room) : payload);
    const roomCode = room.code;
    const matchId = match.matchId;
    const questionId = match.question.questionId;
    match.timer = setTimeout(() => {
      const current = this.rooms.get(roomCode);
      if (current !== room || current.match !== match || match.matchId !== matchId || match.question.questionId !== questionId ||
          match.revision !== scheduledRevision || match.phase !== 'SCHEDULED') return;
      this.openScheduledQuestion(room, scheduledRevision);
    }, Math.max(0, match.opensAt - Date.now()) + 1);
  }

  private openScheduledQuestion(room: Room, scheduledRevision: number) {
    const match = room.match;
    if (!match || match.phase !== 'SCHEDULED' || match.revision !== scheduledRevision ||
        match.opensAt === undefined || Date.now() < match.opensAt) return;
    if (match.requiresReceipts && (!room.host.socket || !room.guest?.socket ||
        !match.received.has(room.host.id) || !match.received.has(room.guest.id))) return;
    clearTimeout(match.timer);
    match.timer = undefined;
    match.phase = 'ANSWERING';
    match.requiresReceipts = false;
    match.revision++;
    this.armQuestionExpiry(room, match.revision);
    this.broadcastMatch(room, 'match:snapshot', this.snapshot(match, room));
  }

  private armQuestionExpiry(room: Room, answeringRevision: number) {
    const match = room.match;
    if (!match || match.phase !== 'ANSWERING' || match.closesAt === undefined) return;
    clearTimeout(match.expiryTimer);
    const roomCode = room.code;
    const matchId = match.matchId;
    const questionId = match.question.questionId;
    match.expiryTimer = setTimeout(() => {
      this.expireQuestion(room, match, questionId, answeringRevision, roomCode, matchId);
    }, Math.max(0, match.closesAt - Date.now()) + 1);
  }

  private expireQuestion(room: Room, match: Match, questionId: number, revision: number, roomCode = room.code, matchId = match.matchId) {
    const current = this.rooms.get(roomCode);
    if (current !== room || current.match !== match || match.matchId !== matchId || match.question.questionId !== questionId ||
        match.revision !== revision || match.phase !== 'ANSWERING' || match.closesAt === undefined || Date.now() < match.closesAt) return;
    clearTimeout(match.expiryTimer);
    match.expiryTimer = undefined;
    match.expiredQuestions.add(questionId);
    while (match.expiredQuestions.size > 32) match.expiredQuestions.delete(match.expiredQuestions.values().next().value as number);
    match.requests.clear();
    match.received.clear();
    match.phase = 'RESOLVING';
    match.attacker = undefined;
    match.closesAt = undefined;
    this.scheduleQuestion(room, 1_500, 'NEXT', 'match:question');
  }

  private questionDuration(difficulty: Difficulty) {
    if (difficulty === 'EASY') return 10_000;
    if (difficulty === 'EXPERT') return 20_000;
    return 15_000;
  }

  private async finishMatch(room: Room, attacker: Role, message?: string) {
    const match = room.match;
    if (!match || match.phase === 'FINISHED') return;
    clearTimeout(match.timer);
    clearTimeout(match.expiryTimer);
    match.timer = undefined;
    match.expiryTimer = undefined;
    match.requests.clear();
    match.received.clear();
    match.phase = 'FINISHED';
    match.opensAt = undefined;
    match.closesAt = undefined;
    match.scheduleReason = undefined;
    match.requiresReceipts = false;
    match.winner = attacker;
    match.message = message;
    match.revision++;
    // Cleanup may remove the room/guest while persistence is in flight.
    const host = room.host;
    const guest = room.guest;
    room.hostReady = false;
    room.guestReady = false;
    host.socket?.emit('match:result', this.snapshot(match, room, 'host'));
    guest?.socket?.emit('match:result', this.snapshot(match, room, 'guest'));
    this.emitState(room.code);
    const settlement = this.accounts.recordMatch({ matchId: match.matchId, hostId: host.accountId, guestId: guest?.accountId, hostName: match.hostName, guestName: match.guestName, winnerId: attacker === 'host' ? host.accountId : guest?.accountId, difficulty: match.difficulty, finishReason: message && match.hostHp > 0 && match.guestHp > 0 ? 'forfeit' : 'normal', hostHp: match.hostHp, guestHp: match.guestHp, startedAt: match.startedAt, matchType: match.ranked ? 'RANKED' : 'UNRANKED' });
    this.pendingSettlements.add(settlement);
    try {
      const recorded = await settlement;
      if (recorded) {
        const { hostProgression, guestProgression, ...rating } = recorded;
        match.rating = rating;
        match.hostProgression = hostProgression;
        match.guestProgression = guestProgression;
        if (match.ranked) { host.rating = recorded.hostRatingAfter ?? host.rating; if (guest) guest.rating = recorded.guestRatingAfter ?? guest.rating; }
      }
    } catch { /* Result remains valid; no progression is fabricated on persistence failure. */ }
    finally { this.pendingSettlements.delete(settlement); }
    match.revision++;
    host.socket?.emit('match:settled', this.snapshot(match, room, 'host'));
    guest?.socket?.emit('match:settled', this.snapshot(match, room, 'guest'));
    if (room.match === match) this.emitState(room.code);
  }

  private snapshot(match: Match, room?: Room, role?: Role) {
    const deadlines = [room?.host.deadline, room?.guest?.deadline].filter((value): value is number => value !== undefined);
    return { matchId: match.matchId, difficulty: match.difficulty, questionId: match.question.questionId, phase: match.phase,
      hostName: match.hostName, guestName: match.guestName, hostRating: room?.host.rating ?? 1000, guestRating: room?.guest?.rating ?? 1000, hostTier: this.tier(room?.host.rating ?? 1000), guestTier: this.tier(room?.guest?.rating ?? 1000),
      question: { left: match.question.left, operation: match.question.operation, right: match.question.right },
      playerHp: match.hostHp, opponentHp: match.guestHp, attacker: match.attacker, winner: match.winner, revision: match.revision,
      rating: match.rating, progression: role === 'host' ? match.hostProgression : role === 'guest' ? match.guestProgression : undefined,
      message: match.message, ranked: match.ranked, matchType: match.ranked ? 'RANKED' : 'UNRANKED', opensAt: match.opensAt ?? null,
      closesAt: match.closesAt ?? null,
      scheduleReason: match.scheduleReason, deadline: deadlines.length ? Math.min(...deadlines) : undefined, serverNow: Date.now() };
  }

  private broadcastMatch(room: Room, event: string, payload: object) {
    room.host.socket?.emit(event, payload);
    room.guest?.socket?.emit(event, payload);
  }

  private rememberRequest(match: Match, requestId: string, response: object) {
    if (!requestId) return;
    match.requests.set(requestId, response);
    while (match.requests.size > 128) match.requests.delete(match.requests.keys().next().value as string);
  }

  private connectionLost(player: Player) {
    player.socket = undefined;
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    const match = room?.match;
    if (!room || !match) {
      this.leaveRoom(player);
      // Retain the connection's temporary identity until a bounded expiry, even in the lobby.
      this.startGrace(player);
      return;
    }
    clearTimeout(match.timer);
    clearTimeout(match.expiryTimer);
    match.timer = undefined;
    match.expiryTimer = undefined;
    match.requests.clear();
    match.received.clear();
    this.startGrace(player);
    if (match.phase === 'FINISHED') return;
    // Damage is committed before broadcasting an attack. A lethal hit cannot be undone by loss.
    if (match.hostHp === 0 || match.guestHp === 0) {
      this.finishMatch(room, match.hostHp === 0 ? 'guest' : 'host');
      return;
    }
    match.phase = 'PAUSED';
    match.opensAt = undefined;
    match.closesAt = undefined;
    match.scheduleReason = undefined;
    match.requiresReceipts = false;
    match.attacker = undefined;
    match.revision++;
    this.broadcastMatch(room, 'match:paused', this.snapshot(match, room));
  }

  private startGrace(player: Player) {
    clearTimeout(player.graceTimer);
    player.deadline = Date.now() + 15_000;
    const deadline = player.deadline;
    const code = player.roomCode;
    const matchId = code ? this.rooms.get(code)?.match?.matchId : undefined;
    player.graceTimer = setTimeout(() => {
      if (this.players.get(player.id) !== player || player.socket || player.deadline !== deadline || player.roomCode !== code) return;
      if (code && this.rooms.get(code)?.match?.matchId !== matchId) return;
      this.expirePlayer(player);
    }, 15_000);
  }

  private expirePlayer(player: Player) {
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    if (room && !room.host.socket && !room.guest?.socket) {
      clearTimeout(room.match?.timer);
      clearTimeout(room.match?.expiryTimer);
      this.rooms.delete(room.code);
      if (room.guest) this.removePlayer(room.guest);
      this.removePlayer(room.host);
      return;
    }
    this.leaveRoom(player);
    this.removePlayer(player);
  }

  private removePlayer(player: Player) {
    clearTimeout(player.graceTimer);
    if (player.accountId && this.accountPlayers.get(player.accountId) === player) this.accountPlayers.delete(player.accountId);
    this.profileSyncs.delete(player.id);
    player.deadline = undefined;
    player.roomCode = undefined;
    player.socket = undefined;
    this.players.delete(player.id);
  }

  private resumeMatch(room: Room) {
    const match = room.match;
    if (!match || match.phase !== 'PAUSED' || !room.host.socket || !room.guest?.socket) return;
    this.scheduleQuestion(room, 1_500, 'RESUME', 'match:resumed');
  }

  // A fresh question is kept locked until both phones have received it.
  @SubscribeMessage('match:received')
  receivedQuestion(@ConnectedSocket() client: Socket, @MessageBody() body: unknown) {
    if (!isStrictPayload(body, ['matchId', 'questionId', 'revision']) || !isUuidV4(body.matchId) ||
        !isPositiveSafeInteger(body.questionId) || !isRevision(body.revision)) return safeError('INVALID_PAYLOAD');
    const unavailable = this.unavailable();
    if (unavailable) return unavailable;
    const player = this.authenticatedPlayer(client);
    if (!player) return this.authError(client);
    const limited = this.rate(client, player.accountId!, 'match_received', [{ scope: 'match-control', limit: 20, windowMs: 60_000 }]);
    if (limited) return limited;
    const room = player?.roomCode ? this.rooms.get(player.roomCode) : undefined;
    const match = room?.match;
    if (!room || !match || (room.host !== player && room.guest !== player)) return safeError('MATCH_UNAVAILABLE');
    if (match.phase !== 'SCHEDULED' || match.scheduleReason !== 'RESUME' || body.matchId !== match.matchId ||
        body.questionId !== match.question.questionId || body.revision !== match.revision) return safeError('STALE_REQUEST');
    match.received.add(player.id);
    if (room.host.socket && room.guest?.socket && match.received.has(room.host.id) && match.received.has(room.guest.id)) {
      this.openScheduledQuestion(room, match.revision);
    }
    return { ok: true };
  }

  async beforeApplicationShutdown() {
    this.prepareShutdown();
    if (this.pendingSettlements.size) {
      await Promise.race([
        Promise.allSettled([...this.pendingSettlements]),
        new Promise<void>(resolve => setTimeout(resolve, 5_000).unref()),
      ]);
    }
  }

  onApplicationShutdown() { this.prepareShutdown(); }

  private prepareShutdown() {
    if (this.shuttingDown) return;
    this.shuttingDown = true;
    this.security.beginShutdown();
    if (this.matchmakingTimer) clearInterval(this.matchmakingTimer);
    this.matchmakingTimer = undefined;
    this.matchmakingQueue.length = 0;
    for (const room of this.rooms.values()) {
      clearTimeout(room.match?.timer);
      clearTimeout(room.match?.expiryTimer);
    }
    for (const player of this.players.values()) clearTimeout(player.graceTimer);
    if (this.server) {
      this.server.emit('server:shutdown', safeError('SERVER_SHUTDOWN'));
    }
    this.rooms.clear();
    this.players.clear();
    this.accountPlayers.clear();
    this.profileSyncs.clear();
    if (this.server) this.server.disconnectSockets(true);
    this.logger.log(JSON.stringify({ event: 'gateway_shutdown', code: 'SERVER_SHUTDOWN' }));
  }

  private makeQuestion(matchId: string, questionId: number, difficulty: Difficulty, previous?: Question): Question {
    let question: Question;
    do {
      const operations = difficulty === 'EXPERT' ? ['ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE'] as const : ['ADD', 'SUBTRACT', 'MULTIPLY'] as const;
      const operation = operations[Math.floor(Math.random() * operations.length)];
      const max = difficulty === 'EASY' ? 10 : difficulty === 'EXPERT' ? 200 : 50;
      const multiplyMax = difficulty === 'EASY' ? 5 : difficulty === 'EXPERT' ? 20 : 12;
      const multiplyMin = difficulty === 'EXPERT' ? 2 : 1;
      let left = operation === 'MULTIPLY' ? this.randomInt(multiplyMin, multiplyMax) : this.randomInt(0, max);
      let right = operation === 'MULTIPLY' ? this.randomInt(multiplyMin, multiplyMax) : this.randomInt(0, max);
      if (operation === 'DIVIDE') { right = this.randomInt(2, 20); left = right * this.randomInt(2, 20); }
      if (operation === 'SUBTRACT') { const ordered = [Math.max(left, right), Math.min(left, right)]; left = ordered[0]; right = ordered[1]; }
      question = { matchId, questionId, left, operation, right };
    } while (previous && question.left === previous.left && question.right === previous.right && question.operation === previous.operation);
    return question;
  }

  private correctAnswer(question: Question) {
    if (question.operation === 'ADD') return question.left + question.right;
    if (question.operation === 'SUBTRACT') return question.left - question.right;
    if (question.operation === 'MULTIPLY') return question.left * question.right;
    return question.left / question.right;
  }

  private randomInt(min: number, max: number) { return Math.floor(Math.random() * (max - min + 1)) + min; }

  private generateCode() {
    return Array.from({ length: 6 }, () => 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'[Math.floor(Math.random() * 36)]).join('');
  }

  private hashSecret(value: string) { return createHash('sha256').update(value).digest('hex'); }
}
