import { MigrationInterface, QueryRunner } from 'typeorm';

export class Ratings1720000000000 implements MigrationInterface {
  name = 'Ratings1720000000000';
  async up(queryRunner: QueryRunner) {
    await queryRunner.query(`ALTER TABLE "players" ADD "rating" integer NOT NULL DEFAULT 1000`);
    await queryRunner.query(`CREATE INDEX "IDX_players_rating" ON "players" ("rating")`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "match_type" character varying(8) NOT NULL DEFAULT 'UNRANKED'`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "host_rating_before" smallint`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "guest_rating_before" smallint`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "host_rating_after" smallint`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "guest_rating_after" smallint`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "host_rating_delta" smallint`);
    await queryRunner.query(`ALTER TABLE "completed_matches" ADD "guest_rating_delta" smallint`);
  }
  async down(queryRunner: QueryRunner) {
    await queryRunner.query(`DROP INDEX "IDX_players_rating"`);
    for (const column of ['guest_rating_delta','host_rating_delta','guest_rating_after','host_rating_after','guest_rating_before','host_rating_before','match_type']) await queryRunner.query(`ALTER TABLE "completed_matches" DROP COLUMN "${column}"`);
    await queryRunner.query(`ALTER TABLE "players" DROP COLUMN "rating"`);
  }
}
