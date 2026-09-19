import { ConnectedSocket, MessageBody, SubscribeMessage, WebSocketGateway, OnGatewayConnection, OnGatewayDisconnect, WebSocketServer } from '@nestjs/websockets';
import { Socket } from 'socket.io';

type Role = 'host' | 'guest';
type Question = { matchId: string; questionId: number; left: number; operation: 'ADD' | 'SUBTRACT' | 'MULTIPLY'; right: number };
type Match = { matchId: string; question: Question; phase: 'answering' | 'resolving'; hostHp: number; guestHp: number; timer?: NodeJS.Timeout };
type Room = { code: string; host: Socket; guest?: Socket; hostReady: boolean; guestReady: boolean; match?: Match };

@WebSocketGateway({ cors: true })
export class ConnectionGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer() private server!: import('socket.io').Server;
  private readonly rooms = new Map<string, Room>();
  private readonly roomBySocket = new Map<string, string>();

  handleConnection(client: Socket) { console.log(`Socket connected: ${client.id}`); }
  handleDisconnect(client: Socket) {
    this.leaveRoom(client);
    console.log(`Socket disconnected: ${client.id}`);
  }

  @SubscribeMessage('connection:check')
  connectionCheck(@ConnectedSocket() _client: Socket) {
    return { ok: true, message: 'Math Fight server ready' };
  }

  @SubscribeMessage('room:create')
  createRoom(@ConnectedSocket() client: Socket) {
    if (this.roomBySocket.has(client.id)) return { ok: false, error: 'already in a room' };
    let code = '';
    do { code = this.generateCode(); } while (this.rooms.has(code));
    this.rooms.set(code, { code, host: client, hostReady: false, guestReady: false });
    this.roomBySocket.set(client.id, code);
    this.emitState(code);
    return { ok: true, room: { code, role: 'host', playerCount: 1 } };
  }

  @SubscribeMessage('room:join')
  joinRoom(@ConnectedSocket() client: Socket, @MessageBody() body: { code?: string }) {
    if (this.roomBySocket.has(client.id)) return { ok: false, error: 'already in a room' };
    const code = String(body?.code ?? '').trim().toUpperCase();
    if (!/^[A-Z0-9]{6}$/.test(code)) return { ok: false, error: 'invalid room code' };
    const room = this.rooms.get(code);
    if (!room) return { ok: false, error: 'room not found' };
    if (room.guest) return { ok: false, error: 'room full' };
    room.guest = client;
    this.roomBySocket.set(client.id, code);
    this.emitState(code);
    return { ok: true, room: { code, role: 'guest', playerCount: 2 } };
  }

  @SubscribeMessage('room:leave')
  leaveRoomMessage(@ConnectedSocket() client: Socket) {
    const code = this.leaveRoom(client);
    return { ok: true, code };
  }

  @SubscribeMessage('room:ready')
  setReady(@ConnectedSocket() client: Socket, @MessageBody() body: { ready?: boolean }) {
    const code = this.roomBySocket.get(client.id);
    const room = code ? this.rooms.get(code) : undefined;
    if (!room) return { ok: false, error: 'not in a room' };
    const ready = Boolean(body?.ready);
    if (room.host.id === client.id) room.hostReady = ready;
    else if (room.guest?.id === client.id) room.guestReady = ready;
    else return { ok: false, error: 'not in a room' };
    this.emitState(room.code);
    if (room.guest && room.hostReady && room.guestReady && !room.match) this.startMatch(room);
    return { ok: true, ready };
  }

  @SubscribeMessage('match:answer')
  answer(@ConnectedSocket() client: Socket, @MessageBody() body: { matchId?: string; questionId?: number; answer?: unknown }) {
    const code = this.roomBySocket.get(client.id);
    const room = code ? this.rooms.get(code) : undefined;
    const match = room?.match;
    if (!room || !match) return { ok: false, error: 'no active match' };
    const role = room.host.id === client.id ? 'host' : room.guest?.id === client.id ? 'guest' : undefined;
    if (!role || match.phase !== 'answering' || body?.matchId !== match.matchId || body?.questionId !== match.question.questionId) {
      return { ok: false, error: 'stale question' };
    }
    const rawAnswer = body.answer;
    const validInteger = typeof rawAnswer === 'number' ? Number.isInteger(rawAnswer) : typeof rawAnswer === 'string' && /^[+-]?\d+$/.test(rawAnswer.trim());
    const answer = typeof rawAnswer === 'number' ? rawAnswer : Number(rawAnswer);
    if (!validInteger || !Number.isInteger(answer)) return { ok: false, error: 'answer must be an integer' };
    if (answer !== this.correctAnswer(match.question)) return { ok: false, error: 'wrong answer', wrong: true };
    match.phase = 'resolving';
    if (role === 'host') match.guestHp = Math.max(0, match.guestHp - 20);
    else match.hostHp = Math.max(0, match.hostHp - 20);
    const ko = match.hostHp === 0 || match.guestHp === 0;
    room.host.emit('match:attack', { attacker: role, playerHp: match.hostHp, opponentHp: match.guestHp, matchId: match.matchId, questionId: match.question.questionId, ko });
    room.guest?.emit('match:attack', { attacker: role, playerHp: match.hostHp, opponentHp: match.guestHp, matchId: match.matchId, questionId: match.question.questionId, ko });
    match.timer = setTimeout(() => {
      const current = this.rooms.get(room.code);
      if (current !== room || current.match !== match || match.phase !== 'resolving') return;
      if (ko) this.finishMatch(room, role);
      else {
        match.question = this.makeQuestion(match.matchId, match.question.questionId + 1, match.question);
        match.phase = 'answering';
        room.host.emit('match:question', match.question);
        room.guest?.emit('match:question', match.question);
      }
    }, 500);
    return { ok: true };
  }

  private leaveRoom(client: Socket): string | undefined {
    const code = this.roomBySocket.get(client.id);
    if (!code) return undefined;
    this.roomBySocket.delete(client.id);
    const room = this.rooms.get(code);
    if (!room) return code;
    this.endMatch(room, client.id, 'Opponent disconnected');
    if (room.host.id === client.id) {
      this.rooms.delete(code);
      if (room.guest) {
        this.roomBySocket.delete(room.guest.id);
        room.guest.emit('room:closed', { code, message: 'Host left the room' });
      }
    } else if (room.guest?.id === client.id) {
      room.guest = undefined;
      room.guestReady = false;
      this.emitState(code);
    }
    return code;
  }

  private emitState(code: string) {
    const room = this.rooms.get(code);
    if (!room) return;
    const playerCount = room.guest ? 2 : 1;
    room.host.emit('room:state', { code, role: 'host', playerCount, hostReady: room.hostReady, guestReady: room.guestReady, matchActive: Boolean(room.match) });
    room.guest?.emit('room:state', { code, role: 'guest', playerCount, hostReady: room.hostReady, guestReady: room.guestReady, matchActive: Boolean(room.match) });
  }

  private startMatch(room: Room) {
    const matchId = `${room.code}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const question = this.makeQuestion(matchId, 1);
    room.match = { matchId, question, phase: 'answering', hostHp: 100, guestHp: 100 };
    this.emitState(room.code);
    const payload = { ...question, playerHp: 100, opponentHp: 100 };
    room.host.emit('match:start', payload);
    room.guest?.emit('match:start', payload);
  }

  private finishMatch(room: Room, attacker: Role) {
    const match = room.match;
    if (!match) return;
    room.host.emit('match:result', { winner: attacker, matchId: match.matchId, playerHp: match.hostHp, opponentHp: match.guestHp });
    room.guest?.emit('match:result', { winner: attacker, matchId: match.matchId, playerHp: match.hostHp, opponentHp: match.guestHp });
    room.match = undefined;
    room.hostReady = false;
    room.guestReady = false;
    this.emitState(room.code);
  }

  private endMatch(room: Room, leavingSocketId: string, message: string) {
    if (room.match) {
      if (room.match.timer) clearTimeout(room.match.timer);
      const other = leavingSocketId === room.host.id ? room.guest : room.host;
      other?.emit('match:ended', { matchId: room.match.matchId, message });
      room.match = undefined;
    }
    room.hostReady = false;
    room.guestReady = false;
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
