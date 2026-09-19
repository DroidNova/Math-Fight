import 'reflect-metadata';
import { DataSource } from 'typeorm';
import { PlayerEntity, CompletedMatchEntity } from './entities';
import { InitialStats1710000000000 } from './migrations/1710000000000-InitialStats';
import { Ratings1720000000000 } from './migrations/1720000000000-Ratings';
import { PlayerXp1730000000000 } from './migrations/1730000000000-PlayerXp';

export default new DataSource({ type: 'postgres', host: process.env.DB_HOST ?? 'localhost', port: Number(process.env.DB_PORT ?? 5432), username: process.env.DB_USER ?? 'mathfight', password: process.env.DB_PASSWORD ?? 'mathfight', database: process.env.DB_NAME ?? 'mathfight', entities: [PlayerEntity, CompletedMatchEntity], migrations: [InitialStats1710000000000, Ratings1720000000000, PlayerXp1730000000000], synchronize: false });
