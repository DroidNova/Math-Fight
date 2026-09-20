import { MigrationInterface, QueryRunner } from 'typeorm';

export class InitialStats1710000000000 implements MigrationInterface {
  name = 'InitialStats1710000000000';
  async up(queryRunner: QueryRunner) {
    await queryRunner.query(`CREATE EXTENSION IF NOT EXISTS "pgcrypto"`);
    await queryRunner.query(`CREATE TABLE "players" ("id" uuid NOT NULL DEFAULT gen_random_uuid(), "profile_id" uuid NOT NULL, "display_name" character varying(16) NOT NULL, "account_token_hash" character varying(64) NOT NULL, "wins" integer NOT NULL DEFAULT 0, "losses" integer NOT NULL DEFAULT 0, "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "updated_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), CONSTRAINT "PK_players_id" PRIMARY KEY ("id"), CONSTRAINT "UQ_players_profile" UNIQUE ("profile_id"), CONSTRAINT "UQ_players_token" UNIQUE ("account_token_hash"))`);
    await queryRunner.query(`CREATE TABLE "completed_matches" ("id" uuid NOT NULL DEFAULT gen_random_uuid(), "match_id" character varying(64) NOT NULL, "host_player_id" uuid NOT NULL, "guest_player_id" uuid NOT NULL, "host_name" character varying(16) NOT NULL, "guest_name" character varying(16) NOT NULL, "winner_id" uuid NOT NULL, "difficulty" character varying(8) NOT NULL, "finish_reason" character varying(8) NOT NULL, "host_hp" smallint NOT NULL, "guest_hp" smallint NOT NULL, "started_at" TIMESTAMP WITH TIME ZONE NOT NULL, "completed_at" TIMESTAMP WITH TIME ZONE NOT NULL, CONSTRAINT "PK_completed_matches_id" PRIMARY KEY ("id"), CONSTRAINT "UQ_completed_match_id" UNIQUE ("match_id"))`);
    await queryRunner.query(`CREATE INDEX "IDX_completed_matches_players" ON "completed_matches" ("host_player_id", "guest_player_id")`);
    await queryRunner.query(`CREATE INDEX "IDX_completed_matches_completed_at" ON "completed_matches" ("completed_at")`);
  }
  async down(queryRunner: QueryRunner) { await queryRunner.query(`DROP TABLE "completed_matches"`); await queryRunner.query(`DROP TABLE "players"`); }
}
