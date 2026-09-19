import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.enableCors();
  const port = Number.parseInt(process.env.PORT ?? '3000', 10) || 3000;
  await app.listen(port, '0.0.0.0');
  console.log(`Math Fight server listening on 0.0.0.0:${port}`);
}

void bootstrap();
