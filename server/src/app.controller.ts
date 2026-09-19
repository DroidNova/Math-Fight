import { Controller, Get, ServiceUnavailableException } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';

@Controller()
export class AppController {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}
  @Get('health')
  async health() { try { await this.dataSource.query('SELECT 1'); return { status: 'ok' }; } catch { throw new ServiceUnavailableException({ status: 'unavailable' }); } }
}
