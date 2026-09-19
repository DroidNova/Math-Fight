import { ConnectedSocket, MessageBody, SubscribeMessage, WebSocketGateway, OnGatewayConnection, OnGatewayDisconnect } from '@nestjs/websockets';
import { OnApplicationShutdown } from '@nestjs/common';
import { randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { Socket } from 'socket.io';

type Role = 'host' | 'guest';
type Question = { matchId: string; questionId: number; left: number; operation: 'ADD' | 'SUBTRACT' | 'MULTIPLY'; right: number };
type Match = { matchId: string; hostName: string; guestName: string; question: Question; phase: 'answering' | 'resolving' | 'paused' | 'resuming' | 'result'; hostHp: number; guestHp: number; revision: number; attacker?: Role; winner?: Role; message?: string; timer?: NodeJS.Timeout; requests: Map<string, object>; received: Set<string> };
type Player = { id: string; token: string; profileId?: string; displayName?: string; socket?: Socket; roomCode?: string; deadline?: number; graceTimer?: NodeJS.Timeout };
type Room = { code: string; host: Player; guest?: Player; hostReady: boolean; guestReady: boolean; match?: Match };

@WebSocketGateway({ cors: true, pingInterval: 3_000, pingTimeout: 5_000 })
export class ConnectionGateway implements OnGatewayConnection, OnGatewayDisconnect, OnApplicationShutdown {
  private readonly rooms = new Map<string, Room>();
  private readonly players = new Map<string, Player>();

  handleConnection(client: Socket) { console.log(`Socket connected: ${client.id}`); }
  handleDisconnect(client: Socket) {
    const player = this.playerFor(client);
    if (player) this.connectionLost(player);
    console.log(`Socket disconnected: ${client.id}`);
  }

  // Only the currently bound socket may act for this temporary session.
  private playerFor(client: Socket) {
    const player = this.players.get(client.data.playerId as string);
    return player?.socket === client ? player : undefined;
  }

  @SubscribeMessage('session:open')
  openSession(@ConnectedSocket() client: Socket, @MessageBody() body: { playerId?: string; resumeToken?: string }) {
    const bound = this.playerFor(client);
    if (bound) return this.sessionResponse(bound);
    let player: Player;
    if (body?.playerId || body?.resumeToken) {
      const existing = this.players.get(String(body.playerId));
      const token = Buffer.from(String(body.resumeToken ?? ''));
      if (!existing || token.length !== existing.token.length ||
          !timingSafeEqual(token, Buffer.from(existing.token))) return { ok: false, error: 'Session expired. Connect again.' };
      if (existing.deadline && Date.now() >= existing.deadline) {
        this.expirePlayer(existing);
        return { ok: false, error: 'Reconnection time expired' };
      }
      player = existing;
      if (player.socket && player.socket !== client) {
        const old = player.socket;
        this.connectionLost(player);
        old.disconnect(true);
      }
    } else {
      player = { id: randomUUID(), token: randomBytes(32).toString('hex') };
      this.players.set(player.id, player);
    }
    clearTimeout(player.graceTimer);
    player.graceTimer = undefined;
    player.deadline = undefined;
    player.socket = client;
    client.data.playerId = player.id;
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    if (room?.match?.phase === 'paused') this.resumeMatch(room);
    // Completed matches can be restored without replaying their presentation.
    if (room?.match?.phase === 'result' || room?.match?.phase === 'paused') room.match.revision++;
    return this.sessionResponse(player);
  }

  private sessionResponse(player: Player) {
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    return { ok: true, playerId: player.id, resumeToken: player.token,
      room: room ? this.roomState(room, player) : undefined,
      snapshot: room?.match ? this.snapshot(room.match, room) : undefined };
  }

  @SubscribeMessage('session:leave')
  leaveSession(@ConnectedSocket() client: Socket) {
    const player = this.playerFor(client);
    if (player) { this.leaveRoom(player); this.removePlayer(player); }
    return { ok: true };
  }

  @SubscribeMessage('profile:sync')
  syncProfile(@ConnectedSocket() client: Socket, @MessageBody() body: { profileId?: unknown; displayName?: unknown }) {
    const player = this.playerFor(client);
    if (!player) return { ok: false, error: 'Connect again to start a session' };
    if (typeof body?.profileId !== 'string' || body.profileId.length !== 36 || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.profileId)) {
      return { ok: false, error: 'Invalid profile ID: expected a UUID' };
    }
    // Only literal spaces are normalized: tabs, newlines, controls and symbols stay invalid.
    const name = typeof body?.displayName === 'string' ? body.displayName.replace(/^ +| +$/g, '').replace(/ +/g, ' ') : '';
    if (Array.from(name).length < 3 || Array.from(name).length > 16 || /[^\p{L}\p{N}_ ]/u.test(name)) {
      return { ok: false, error: 'Use 3–16 letters or numbers, spaces, or underscore.' };
    }
    const profileId = body.profileId.toLowerCase();
    const room = player.roomCode ? this.rooms.get(player.roomCode) : undefined;
    if (room?.match && room.match.phase !== 'result' && (player.profileId !== profileId || player.displayName !== name)) {
      return { ok: false, error: 'Finish the active match before changing your profile' };
    }
    // This ID is metadata only. Socket ownership and resume-token checks remain authoritative.
    player.profileId = profileId;
    player.displayName = name;
    if (room) this.emitState(room.code);
    return { ok: true, displayName: name, room: room ? this.roomState(room, player) : undefined };
  }

  @SubscribeMessage('connection:check')
  connectionCheck(@ConnectedSocket() _client: Socket) {
    return { ok: true, message: 'Math Fight server ready' };
  }

  @SubscribeMessage('room:create')
  createRoom(@ConnectedSocket() client: Socket) {
    const player = this.playerFor(client);
    if (!player) return { ok: false, error: 'Connect again to start a session' };
    if (!player.displayName) return { ok: false, error: 'Set up your player profile first' };
    if (player.roomCode) return { ok: false, error: 'already in a room' };
    let code = '';
    do { code = this.generateCode(); } while (this.rooms.has(code));
    this.rooms.set(code, { code, host: player, hostReady: false, guestReady: false });
    player.roomCode = code;
    this.emitState(code);
    return { ok: true, room: this.roomState(this.rooms.get(code)!, player) };
  }

  @SubscribeMessage('room:join')
  joinRoom(@ConnectedSocket() client: Socket, @MessageBody() body: { code?: string }) {
    const player = this.playerFor(client);
    if (!player) return { ok: false, error: 'Connect again to start a session' };
    if (!player.displayName) return { ok: false, error: 'Set up your player profile first' };
    if (player.roomCode) return { ok: false, error: 'already in a room' };
    const code = String(body?.code ?? '').trim().toUpperCase();
    if (!/^[A-Z0-9]{6}$/.test(code)) return { ok: false, error: 'invalid room code' };
    const room = this.rooms.get(code);
    if (!room) return { ok: false, error: 'room not found' };
    if (room.guest) return { ok: false, error: 'room full' };
    room.guest = player;
    player.roomCode = code;
    this.emitState(code);
    return { ok: true, room: this.roomState(room, player) };
  }

  @SubscribeMessage('room:leave')
  leaveRoomMessage(@ConnectedSocket() client: Socket) {
    return this.leaveSession(client);
  }

  @SubscribeMessage('room:ready')
  setReady(@ConnectedSocket() client: Socket, @MessageBody() body: { ready?: boolean }) {
    const player = this.playerFor(client);
    const code = player?.roomCode;
    const room = code ? this.rooms.get(code) : undefined;
    if (!room || !player) return { ok: false, error: 'not in a room' };
    if (room.match && room.match.phase !== 'result') return { ok: false, error: 'match already active' };
    const ready = Boolean(body?.ready);
    if (room.host === player) room.hostReady = ready;
    else if (room.guest === player) room.guestReady = ready;
    else return { ok: false, error: 'not in a room' };
    this.emitState(room.code);
    if (room.host.socket && room.guest?.socket && room.hostReady && room.guestReady && (!room.match || room.match.phase === 'result')) this.startMatch(room);
    return { ok: true, ready };
  }

  @SubscribeMessage('match:answer')
  answer(@ConnectedSocket() client: Socket, @MessageBody() body: { requestId?: string; matchId?: string; questionId?: number; answer?: unknown }) {
    const requestId = String(body?.requestId ?? '');
    const base = { requestId, matchId: body?.matchId, questionId: body?.questionId };
    const player = this.playerFor(client);
    const code = player?.roomCode;
    const room = code ? this.rooms.get(code) : undefined;
    const match = room?.match;
    if (!room || !match || !player) return { ok: false, result: 'invalid', error: 'no active match', ...base };
    if (body?.matchId !== match.matchId || body?.questionId !== match.question.questionId || match.phase === 'paused' || match.phase === 'resuming') {
      return { ok: false, result: 'invalid', error: 'stale or paused question', ...base };
    }
    const requestKey = `${player.id}:${requestId}`;
    const previous = requestId ? match.requests.get(requestKey) : undefined;
    if (previous) return previous;
    const role = room.host === player ? 'host' : room.guest === player ? 'guest' : undefined;
    if (!requestId || !role || match.phase !== 'answering' || body?.matchId !== match.matchId || body?.questionId !== match.question.questionId) {
      return { ok: false, result: match.phase === 'resolving' ? 'already_resolved' : 'invalid', error: 'stale question', ...base };
    }
    const rawAnswer = body.answer;
    const validInteger = typeof rawAnswer === 'number' ? Number.isInteger(rawAnswer) : typeof rawAnswer === 'string' && /^[+-]?\d+$/.test(rawAnswer.trim());
    const answer = typeof rawAnswer === 'number' ? rawAnswer : Number(rawAnswer);
    if (!validInteger || !Number.isInteger(answer)) return { ok: false, result: 'invalid', error: 'answer must be an integer', ...base };
    if (answer !== this.correctAnswer(match.question)) {
      const response = { ok: false, result: 'incorrect', wrong: true, error: 'wrong answer', ...base };
      this.rememberRequest(match, requestKey, response);
      return response;
    }
    const accepted = { ok: true, result: 'correct', ...base };
    this.rememberRequest(match, requestKey, accepted);
    match.phase = 'resolving';
    match.attacker = role;
    match.revision++;
    if (role === 'host') match.guestHp = Math.max(0, match.guestHp - 20);
    else match.hostHp = Math.max(0, match.hostHp - 20);
    const ko = match.hostHp === 0 || match.guestHp === 0;
    this.broadcastMatch(room, 'match:attack', { attacker: role, playerHp: match.hostHp, opponentHp: match.guestHp, matchId: match.matchId, questionId: match.question.questionId, ko, revision: match.revision });
    const questionId = match.question.questionId;
    match.timer = setTimeout(() => {
      const current = this.rooms.get(room.code);
      if (current !== room || current.match !== match || match.phase !== 'resolving' || match.question.questionId !== questionId) return;
      if (ko) this.finishMatch(room, role);
      else {
        match.question = this.makeQuestion(match.matchId, match.question.questionId + 1, match.question);
        match.phase = 'answering';
        match.attacker = undefined;
        match.revision++;
        this.broadcastMatch(room, 'match:question', { ...match.question, playerHp: match.hostHp, opponentHp: match.guestHp, revision: match.revision });
      }
    }, 500);
    return accepted;
  }

  @SubscribeMessage('match:state')
  matchState(@ConnectedSocket() client: Socket) {
    const code = this.playerFor(client)?.roomCode;
    const room = code ? this.rooms.get(code) : undefined;
    if (!room?.match) return { ok: false, result: 'ended', error: 'match unavailable' };
    return { ok: true, snapshot: this.snapshot(room.match, room) };
  }

  private leaveRoom(player: Player): string | undefined {
    const code = player.roomCode;
    if (!code) return undefined;
    player.roomCode = undefined;
    clearTimeout(player.graceTimer);
    player.deadline = undefined;
    const room = this.rooms.get(code);
    if (!room) return code;
    if (room.match && room.match.phase !== 'result') {
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
      hostName: room.host.displayName, guestName: room.guest?.displayName,
      hostReady: room.hostReady, guestReady: room.guestReady, matchActive: Boolean(room.match && room.match.phase !== 'result') };
  }

  private startMatch(room: Room) {
    const matchId = randomUUID();
    const question = this.makeQuestion(matchId, 1);
    room.match = { matchId, hostName: room.host.displayName!, guestName: room.guest!.displayName!, question, phase: 'answering', hostHp: 100, guestHp: 100, revision: 1, requests: new Map(), received: new Set() };
    this.emitState(room.code);
    const payload = { ...question, hostName: room.match.hostName, guestName: room.match.guestName, playerHp: 100, opponentHp: 100, revision: room.match.revision };
    this.broadcastMatch(room, 'match:start', payload);
  }

  private finishMatch(room: Room, attacker: Role, message?: string) {
    const match = room.match;
    if (!match || match.phase === 'result') return;
    clearTimeout(match.timer);
    match.requests.clear();
    match.received.clear();
    match.phase = 'result';
    match.winner = attacker;
    match.message = message;
    match.revision++;
    this.broadcastMatch(room, 'match:result', this.snapshot(match, room));
    room.hostReady = false;
    room.guestReady = false;
    this.emitState(room.code);
  }

  private snapshot(match: Match, room?: Room) {
    const deadlines = [room?.host.deadline, room?.guest?.deadline].filter((value): value is number => value !== undefined);
    return { matchId: match.matchId, questionId: match.question.questionId, phase: match.phase,
      hostName: match.hostName, guestName: match.guestName,
      question: { left: match.question.left, operation: match.question.operation, right: match.question.right },
      playerHp: match.hostHp, opponentHp: match.guestHp, attacker: match.attacker, winner: match.winner, revision: match.revision,
      message: match.message, deadline: deadlines.length ? Math.min(...deadlines) : undefined, serverNow: Date.now() };
  }

  private broadcastMatch(room: Room, event: string, payload: object) {
    room.host.socket?.emit(event, payload);
    room.guest?.socket?.emit(event, payload);
  }

  private rememberRequest(match: Match, requestId: string, response: object) {
    if (!requestId) return;
    match.requests.set(requestId, response);
    while (match.requests.size > 32) match.requests.delete(match.requests.keys().next().value as string);
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
    match.requests.clear();
    match.received.clear();
    this.startGrace(player);
    if (match.phase === 'result') return;
    // Damage is committed before broadcasting an attack. A lethal hit cannot be undone by loss.
    if (match.hostHp === 0 || match.guestHp === 0) {
      this.finishMatch(room, match.hostHp === 0 ? 'guest' : 'host');
      return;
    }
    match.phase = 'paused';
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
    player.deadline = undefined;
    player.roomCode = undefined;
    player.socket = undefined;
    this.players.delete(player.id);
  }

  private resumeMatch(room: Room) {
    const match = room.match;
    if (!match || match.phase !== 'paused' || !room.host.socket || !room.guest?.socket) return;
    match.question = this.makeQuestion(match.matchId, match.question.questionId + 1, match.question);
    match.phase = 'resuming';
    match.attacker = undefined;
    match.requests.clear();
    match.received.clear();
    match.revision++;
    this.broadcastMatch(room, 'match:resumed', this.snapshot(match, room));
  }

  // A fresh question is kept locked until both phones have received it.
  @SubscribeMessage('match:received')
  receivedQuestion(@ConnectedSocket() client: Socket, @MessageBody() body: { matchId?: string; questionId?: number; revision?: number }) {
    const player = this.playerFor(client);
    const room = player?.roomCode ? this.rooms.get(player.roomCode) : undefined;
    const match = room?.match;
    if (!player || !room || !match || match.phase !== 'resuming' || body?.matchId !== match.matchId ||
        body?.questionId !== match.question.questionId || body?.revision !== match.revision) return { ok: false };
    match.received.add(player.id);
    if (room.host.socket && room.guest?.socket && match.received.has(room.host.id) && match.received.has(room.guest.id)) {
      match.phase = 'answering';
      match.revision++;
      this.broadcastMatch(room, 'match:snapshot', this.snapshot(match, room));
    }
    return { ok: true };
  }

  onApplicationShutdown() {
    for (const room of this.rooms.values()) clearTimeout(room.match?.timer);
    for (const player of this.players.values()) clearTimeout(player.graceTimer);
    this.rooms.clear();
    this.players.clear();
  }

  private makeQuestion(matchId: string, questionId: number, previous?: Question): Question {
    let question: Question;
    do {
      const operation = (['ADD', 'SUBTRACT', 'MULTIPLY'] as const)[Math.floor(Math.random() * 3)];
      const left = operation === 'MULTIPLY' ? this.randomInt(1, 10) : this.randomInt(0, 20);
      const rawRight = operation === 'MULTIPLY' ? this.randomInt(1, 10) : this.randomInt(0, 20);
      const right = operation === 'SUBTRACT' ? Math.min(left, rawRight) : rawRight;
      question = { matchId, questionId, left, operation, right };
    } while (previous && question.left === previous.left && question.right === previous.right && question.operation === previous.operation);
    return question;
  }

  private correctAnswer(question: Question) {
    if (question.operation === 'ADD') return question.left + question.right;
    if (question.operation === 'SUBTRACT') return question.left - question.right;
    return question.left * question.right;
  }

  private randomInt(min: number, max: number) { return Math.floor(Math.random() * (max - min + 1)) + min; }

  private generateCode() {
    return Array.from({ length: 6 }, () => 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'[Math.floor(Math.random() * 36)]).join('');
  }
}
