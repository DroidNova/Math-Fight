import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { ConnectionGateway } from './connection.gateway';

@Module({ controllers: [AppController], providers: [ConnectionGateway] })
export class AppModule {}
