import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AppController } from './app.controller';
import { ConnectionGateway } from './connection.gateway';
import { AccountService } from './account.service';
import { PlayerEntity, CompletedMatchEntity } from './entities';

@Module({ imports: [TypeOrmModule.forRoot({ type: 'postgres', host: process.env.DB_HOST ?? 'localhost', port: Number(process.env.DB_PORT ?? 5432), username: process.env.DB_USER ?? 'mathfight', password: process.env.DB_PASSWORD ?? 'mathfight', database: process.env.DB_NAME ?? 'mathfight', entities: [PlayerEntity, CompletedMatchEntity], synchronize: false }), TypeOrmModule.forFeature([PlayerEntity, CompletedMatchEntity])], controllers: [AppController], providers: [ConnectionGateway, AccountService] })
export class AppModule {}
