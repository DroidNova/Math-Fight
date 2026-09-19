import { ConnectedSocket, MessageBody, SubscribeMessage, WebSocketGateway, OnGatewayConnection, OnGatewayDisconnect, WebSocketServer } from '@nestjs/websockets';
import { Socket } from 'socket.io';

type Room = { code: string; host: Socket; guest?: Socket };

@WebSocketGateway({ cors: true })
export class ConnectionGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer() private server!: import('socket.io').Server;
  private readonly rooms = new Map<string, Room>();
  private readonly roomBySocket = new Map<string, string>();

  handleConnection(client: Socket) { console.log(`Socket connected: ${client.id}`); }
  handleDisconnect(client: Socket) {
    this.leaveRoom(client, false);
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
    this.rooms.set(code, { code, host: client });
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
    const code = this.leaveRoom(client, true);
    return { ok: true, code };
  }

  private leaveRoom(client: Socket, notify: boolean): string | undefined {
    const code = this.roomBySocket.get(client.id);
    if (!code) return undefined;
    this.roomBySocket.delete(client.id);
    const room = this.rooms.get(code);
    if (!room) return code;
    if (room.host.id === client.id) {
      this.rooms.delete(code);
      if (room.guest) {
        this.roomBySocket.delete(room.guest.id);
        room.guest.emit('room:closed', { code, message: 'Host left the room' });
      }
    } else if (room.guest?.id === client.id) {
      room.guest = undefined;
      this.emitState(code);
    }
    return code;
  }

  private emitState(code: string) {
    const room = this.rooms.get(code);
    if (!room) return;
    const playerCount = room.guest ? 2 : 1;
    room.host.emit('room:state', { code, role: 'host', playerCount });
    room.guest?.emit('room:state', { code, role: 'guest', playerCount });
  }

  private generateCode() {
    return Array.from({ length: 6 }, () => 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'[Math.floor(Math.random() * 36)]).join('');
  }
}
