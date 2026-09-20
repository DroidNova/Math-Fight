import { MigrationInterface, QueryRunner } from 'typeorm';

export class PlayerXp1730000000000 implements MigrationInterface {
  async up(queryRunner: QueryRunner) {
    await queryRunner.query(`ALTER TABLE players ADD total_xp integer NOT NULL DEFAULT 0 CHECK (total_xp >= 0)`);
    for (const column of ['host_xp_awarded', 'guest_xp_awarded', 'host_total_xp_before', 'guest_total_xp_before', 'host_total_xp_after', 'guest_total_xp_after']) {
      await queryRunner.query(`ALTER TABLE completed_matches ADD ${column} integer NOT NULL DEFAULT 0 CHECK (${column} >= 0)`);
    }
  }
  async down(queryRunner: QueryRunner) {
    for (const column of ['host_xp_awarded', 'guest_xp_awarded', 'host_total_xp_before', 'guest_total_xp_before', 'host_total_xp_after', 'guest_total_xp_after']) {
      await queryRunner.query(`ALTER TABLE completed_matches DROP COLUMN ${column}`);
    }
    await queryRunner.query(`ALTER TABLE players DROP COLUMN total_xp`);
  }
}
