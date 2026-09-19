import { Entity, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn, Index } from 'typeorm';

@Entity('players')
export class PlayerEntity {
  @PrimaryGeneratedColumn('uuid') id!: string;
  @Index({ unique: true }) @Column({ name: 'profile_id', type: 'uuid' }) profileId!: string;
  @Column({ name: 'display_name', length: 16 }) displayName!: string;
  @Index({ unique: true }) @Column({ name: 'account_token_hash', length: 64 }) accountTokenHash!: string;
  @Column({ default: 0 }) wins!: number;
  @Column({ default: 0 }) losses!: number;
  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' }) createdAt!: Date;
  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' }) updatedAt!: Date;
}

@Entity('completed_matches')
export class CompletedMatchEntity {
  @PrimaryGeneratedColumn('uuid') id!: string;
  @Index({ unique: true }) @Column({ name: 'match_id', length: 64 }) matchId!: string;
  @Column({ name: 'host_player_id', type: 'uuid' }) hostPlayerId!: string;
  @Column({ name: 'guest_player_id', type: 'uuid' }) guestPlayerId!: string;
  @Column({ name: 'host_name', length: 16 }) hostName!: string;
  @Column({ name: 'guest_name', length: 16 }) guestName!: string;
  @Column({ name: 'winner_id', type: 'uuid' }) winnerId!: string;
  @Column({ length: 8 }) difficulty!: string;
  @Column({ name: 'finish_reason', length: 8 }) finishReason!: 'normal' | 'forfeit';
  @Column({ name: 'host_hp', type: 'smallint' }) hostHp!: number;
  @Column({ name: 'guest_hp', type: 'smallint' }) guestHp!: number;
  @Index() @Column({ name: 'started_at', type: 'timestamptz' }) startedAt!: Date;
  @Index() @Column({ name: 'completed_at', type: 'timestamptz' }) completedAt!: Date;
}
