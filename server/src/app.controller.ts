import { BadRequestException, Controller, Get, Req, ServiceUnavailableException } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { SecurityService } from './security';

type HealthRequest = { body?: unknown; query?: Record<string, unknown> };

@Controller()
export class AppController {
  constructor(@InjectDataSource() private readonly dataSource: DataSource,
              private readonly security: SecurityService) {}

  @Get('health/live')
  live(@Req() request: HealthRequest) {
    this.requireEmptyRequest(request);
    return { status: 'ok' };
  }

  @Get('health/ready')
  ready(@Req() request: HealthRequest) { return this.readiness(request); }

  @Get('health')
  health(@Req() request: HealthRequest) { return this.readiness(request); }

  private async readiness(request: HealthRequest) {
    this.requireEmptyRequest(request);
    if (!this.security.isAcceptingWork()) throw new ServiceUnavailableException();
    try {
      await this.dataSource.query('SELECT 1');
      return { status: 'ok' };
    } catch {
      throw new ServiceUnavailableException();
    }
  }

  private requireEmptyRequest(request: HealthRequest) {
    const body = request.body as unknown;
    const hasBody = body !== undefined && (body === null || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).length > 0);
    if (Object.keys(request.query ?? {}).length || hasBody) {
      throw new BadRequestException();
    }
  }
}
