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

  async recordMatch(input: { matchId: string; hostId?: string; guestId?: string; hostName: string; guestName: string; winnerId?: string; difficulty: string; finishReason: 'normal'|'forfeit'; hostHp: number; guestHp: number; startedAt: Date; matchType: 'RANKED'|'UNRANKED' }) {
    if (!input.hostId || !input.guestId || !input.winnerId) return undefined;
    return this.dataSource.transaction(async manager => {
      const existing = await manager.findOne(CompletedMatchEntity, { where: { matchId: input.matchId } });
      if (existing) return { recorded: false, matchType: existing.matchType, hostRatingBefore: existing.hostRatingBefore, guestRatingBefore: existing.guestRatingBefore, hostRatingAfter: existing.hostRatingAfter, guestRatingAfter: existing.guestRatingAfter, hostRatingDelta: existing.hostRatingDelta, guestRatingDelta: existing.guestRatingDelta };
      const loserId = input.winnerId === input.hostId ? input.guestId : input.hostId;
      const host = await manager.findOne(PlayerEntity, { where: { id: input.hostId }, lock: { mode: 'pessimistic_write' } });
      const guest = await manager.findOne(PlayerEntity, { where: { id: input.guestId }, lock: { mode: 'pessimistic_write' } });
      if (!host || !guest) return undefined;
      const ranked = input.matchType === 'RANKED';
      const hostBefore = host.rating;
      const guestBefore = guest.rating;
      const winnerBefore = input.winnerId === host.id ? host.rating : guest.rating;
      const loserBefore = input.winnerId === host.id ? guest.rating : host.rating;
      const delta = ranked ? Math.round(32 * (1 - 1 / (1 + Math.pow(10, (loserBefore - winnerBefore) / 400)))) : 0;
      const hostAfter = ranked ? (input.winnerId === host.id ? host.rating + delta : Math.max(0, host.rating - delta)) : host.rating;
      const guestAfter = ranked ? (input.winnerId === guest.id ? guest.rating + delta : Math.max(0, guest.rating - delta)) : guest.rating;
      await manager.insert(CompletedMatchEntity, { matchId: input.matchId, hostPlayerId: input.hostId, guestPlayerId: input.guestId, hostName: input.hostName, guestName: input.guestName, winnerId: input.winnerId, difficulty: input.difficulty, finishReason: input.finishReason, matchType: input.matchType, hostRatingBefore: ranked ? hostBefore : null, guestRatingBefore: ranked ? guestBefore : null, hostRatingAfter: ranked ? hostAfter : null, guestRatingAfter: ranked ? guestAfter : null, hostRatingDelta: ranked ? hostAfter - hostBefore : null, guestRatingDelta: ranked ? guestAfter - guestBefore : null, hostHp: input.hostHp, guestHp: input.guestHp, startedAt: input.startedAt, completedAt: new Date() });
      await manager.increment(PlayerEntity, { id: input.winnerId }, 'wins', 1);
      await manager.increment(PlayerEntity, { id: loserId }, 'losses', 1);
      if (ranked) { await manager.update(PlayerEntity, { id: host.id }, { rating: hostAfter }); await manager.update(PlayerEntity, { id: guest.id }, { rating: guestAfter }); }
      return { recorded: true, matchType: input.matchType, hostRatingBefore: ranked ? hostBefore : null, guestRatingBefore: ranked ? guestBefore : null, hostRatingAfter: ranked ? hostAfter : null, guestRatingAfter: ranked ? guestAfter : null, hostRatingDelta: ranked ? hostAfter - hostBefore : null, guestRatingDelta: ranked ? guestAfter - guestBefore : null };
    });
  }

  async stats(playerId: string) {
    const player = await this.players.findOne({ where: { id: playerId } });
    if (!player) return undefined;
    const matches = await this.matches.find({ where: [{ hostPlayerId: playerId }, { guestPlayerId: playerId }], order: { completedAt: 'DESC' }, take: 10 });
    const position = await this.position(player);
    return { matchesPlayed: player.wins + player.losses, wins: player.wins, losses: player.losses, rating: player.rating, tier: this.tier(player.rating), leaderboardPosition: position, winRate: player.wins + player.losses ? player.wins / (player.wins + player.losses) : 0, matches: matches.map(match => ({ matchId: match.matchId, result: match.winnerId === playerId ? 'WIN' : 'LOSS', opponentName: match.hostPlayerId === playerId ? match.guestName : match.hostName, difficulty: match.difficulty, finishReason: match.finishReason, matchType: match.matchType, ratingChange: match.hostPlayerId === playerId ? match.hostRatingDelta : match.guestRatingDelta, completedAt: match.completedAt })) };
  }

  async leaderboard(playerId: string) {
    const players = await this.players.find({ order: { rating: 'DESC', wins: 'DESC', losses: 'ASC', createdAt: 'ASC', id: 'ASC' }, take: 100 });
    const current = await this.players.findOne({ where: { id: playerId } });
    const all = current && !players.some(p => p.id === playerId) ? await this.players.find({ order: { rating: 'DESC', wins: 'DESC', losses: 'ASC', createdAt: 'ASC', id: 'ASC' } }) : players;
    const position = current ? all.findIndex(p => p.id === playerId) + 1 : 0;
    return { players: players.map((p, index) => this.leaderboardRow(p, index + 1, p.id === playerId)), currentPosition: position };
  }

  tier(rating: number) { return rating < 900 ? 'Bronze' : rating < 1100 ? 'Silver' : rating < 1300 ? 'Gold' : rating < 1500 ? 'Platinum' : 'Diamond'; }
  private leaderboardRow(player: PlayerEntity, position: number, current: boolean) { return { position, displayName: player.displayName, rating: player.rating, tier: this.tier(player.rating), wins: player.wins, losses: player.losses, current }; }
  private async position(player: PlayerEntity) { const all = await this.players.find({ order: { rating: 'DESC', wins: 'DESC', losses: 'ASC', createdAt: 'ASC', id: 'ASC' } }); return all.findIndex(item => item.id === player.id) + 1; }

  private hash(value: string) { return createHash('sha256').update(value).digest('hex'); }
}
