import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import helmet from 'helmet';
import { AppModule } from './app.module';
import { SafeHttpExceptionFilter } from './safe-http.filter';
import { safeError, SecurityService } from './security';
import { allowOrigin, serverConfig } from './server-config';

type MiddlewareResponse = {
  status(code: number): MiddlewareResponse;
  json(body: unknown): void;
};
type BodyParserApplication = {
  useBodyParser(parser: 'json', options: { limit: string; strict: boolean }): unknown;
};

async function bootstrap() {
  const config = serverConfig();
  const app = await NestFactory.create(AppModule, { bodyParser: false });
  const security = app.get(SecurityService);
  const expressApp = app.getHttpAdapter().getInstance();
  expressApp.disable('x-powered-by');
  if (config.trustProxyHops > 0) expressApp.set('trust proxy', config.trustProxyHops);
  app.use(helmet());
  (app as typeof app & BodyParserApplication).useBodyParser('json', { limit: '16kb', strict: true });
  app.use((error: unknown, _request: unknown, response: MiddlewareResponse, next: () => void) => {
    if (!error) return next();
    response.status(400).json(safeError('INVALID_PAYLOAD'));
  });
  app.useGlobalFilters(new SafeHttpExceptionFilter());
  app.enableCors({
    credentials: false,
    origin: (origin: string | undefined, callback: (error: Error | null, allowed?: boolean) => void) =>
      callback(null, allowOrigin(origin)),
  });
  await app.listen(config.port, '0.0.0.0');
  Logger.log(JSON.stringify({ event: 'server_started', port: config.port }), 'Bootstrap');

  let closing = false;
  const shutdown = async (signal: string) => {
    if (closing) return;
    closing = true;
    security.beginShutdown();
    Logger.log(JSON.stringify({ event: 'server_shutdown', signal }), 'Bootstrap');
    const timeout = setTimeout(() => process.exit(1), 10_000);
    try {
      await app.close();
      clearTimeout(timeout);
    } catch {
      clearTimeout(timeout);
      process.exitCode = 1;
    }
  };
  process.once('SIGTERM', () => void shutdown('SIGTERM'));
  process.once('SIGINT', () => void shutdown('SIGINT'));
}

void bootstrap();
