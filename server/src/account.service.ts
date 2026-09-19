import { Injectable } from '@nestjs/common';
import { InjectDataSource, InjectRepository } from '@nestjs/typeorm';
import { DataSource, Repository } from 'typeorm';
import { createHash, randomBytes } from 'node:crypto';
import { CompletedMatchEntity, PlayerEntity } from './entities';

@Injectable()
export class AccountService {
  constructor(@InjectRepository(PlayerEntity) private readonly players: Repository<PlayerEntity>,
              @InjectRepository(CompletedMatchEntity) private readonly matches: Repository<CompletedMatchEntity>,
              @InjectDataSource() private readonly dataSource: DataSource) {}

  async authenticate(profileId: string, displayName: string, rawToken?: string) {
    let player = await this.players.findOne({ where: { profileId } });
    let issuedToken: string | undefined;
    if (!player) {
      issuedToken = randomBytes(32).toString('hex');
      player = this.players.create({ profileId, displayName, accountTokenHash: this.hash(issuedToken) });
      await this.players.save(player);
    } else {
      if (!rawToken || this.hash(rawToken) !== player.accountTokenHash) throw new Error('Invalid account token');
      player.displayName = displayName;
      await this.players.save(player);
    }
    return { player, issuedToken };
  }

  async recordMatch(input: { matchId: string; hostId?: string; guestId?: string; hostName: string; guestName: string; winnerId?: string; difficulty: string; finishReason: 'normal'|'forfeit'; hostHp: number; guestHp: number; startedAt: Date }) {
    if (!input.hostId || !input.guestId || !input.winnerId) return;
    await this.dataSource.transaction(async manager => {
      const existing = await manager.findOne(CompletedMatchEntity, { where: { matchId: input.matchId } });
      if (existing) return;
      const loserId = input.winnerId === input.hostId ? input.guestId : input.hostId;
      await manager.insert(CompletedMatchEntity, { matchId: input.matchId, hostPlayerId: input.hostId, guestPlayerId: input.guestId, hostName: input.hostName, guestName: input.guestName, winnerId: input.winnerId, difficulty: input.difficulty, finishReason: input.finishReason, hostHp: input.hostHp, guestHp: input.guestHp, startedAt: input.startedAt, completedAt: new Date() });
      await manager.increment(PlayerEntity, { id: input.winnerId }, 'wins', 1);
      await manager.increment(PlayerEntity, { id: loserId }, 'losses', 1);
    });
  }

  async stats(playerId: string) {
    const player = await this.players.findOne({ where: { id: playerId } });
    if (!player) return undefined;
    const matches = await this.matches.find({ where: [{ hostPlayerId: playerId }, { guestPlayerId: playerId }], order: { completedAt: 'DESC' }, take: 10 });
    return { matchesPlayed: player.wins + player.losses, wins: player.wins, losses: player.losses, winRate: player.wins + player.losses ? player.wins / (player.wins + player.losses) : 0, matches: matches.map(match => ({ matchId: match.matchId, result: match.winnerId === playerId ? 'WIN' : 'LOSS', opponentName: match.hostPlayerId === playerId ? match.guestName : match.hostName, difficulty: match.difficulty, finishReason: match.finishReason, completedAt: match.completedAt })) };
  }

  private hash(value: string) { return createHash('sha256').update(value).digest('hex'); }
}
