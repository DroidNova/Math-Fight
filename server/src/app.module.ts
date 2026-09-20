import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { ConnectionGateway } from './connection.gateway';
import { AccountService } from './account.service';
import { PlayerEntity, CompletedMatchEntity } from './entities';
import { SecurityService } from './security';
import { serverConfig } from './server-config';

const config = serverConfig();

@Module({ imports: [TypeOrmModule.forRoot({ type: 'postgres', host: config.dbHost, port: config.dbPort, username: config.dbUser, password: config.dbPassword, database: config.dbName, entities: [PlayerEntity, CompletedMatchEntity], synchronize: false }), TypeOrmModule.forFeature([PlayerEntity, CompletedMatchEntity])], controllers: [AppController], providers: [ConnectionGateway, AccountService, SecurityService] })
export class AppModule {}
