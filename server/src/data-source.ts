import 'reflect-metadata';
import { DataSource } from 'typeorm';
import { PlayerEntity, CompletedMatchEntity } from './entities';
import { InitialStats1710000000000 } from './migrations/1710000000000-InitialStats';
import { Ratings1720000000000 } from './migrations/1720000000000-Ratings';
import { PlayerXp1730000000000 } from './migrations/1730000000000-PlayerXp';
import { serverConfig } from './server-config';

const config = serverConfig();

export default new DataSource({ type: 'postgres', host: config.dbHost, port: config.dbPort, username: config.dbUser, password: config.dbPassword, database: config.dbName, entities: [PlayerEntity, CompletedMatchEntity], migrations: [InitialStats1710000000000, Ratings1720000000000, PlayerXp1730000000000], synchronize: false });
