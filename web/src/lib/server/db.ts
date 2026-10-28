import "server-only";
import { io } from "next/cache";
import { MongoClient, type Db } from "mongodb";

const clients = new Map<string, Promise<MongoClient>>();

async function database(uri: string | undefined, name: string): Promise<Db> {
  await io();
  if (!uri) throw new Error("Database is not configured");
  let pending = clients.get(uri);
  if (!pending) {
    const client = new MongoClient(uri, {
      maxPoolSize: 10,
      serverSelectionTimeoutMS: 3_000,
      connectTimeoutMS: 3_000,
      timeoutMS: 5_000,
    });
    pending = client.connect().catch(async () => {
      clients.delete(uri);
      await client.close();
      throw new Error("Database is unavailable");
    });
    clients.set(uri, pending);
  }
  return (await pending).db(name);
}

export function getGameDb(): Promise<Db> {
  return database(
    process.env.GAME_MONGO_URI ?? process.env.MONGO_URI,
    process.env.MONGO_DB_NAME ?? "xcore",
  );
}

export function getWebDb(): Promise<Db> {
  return database(
    process.env.WEB_MONGO_URI ?? process.env.MONGO_URI,
    process.env.WEB_MONGO_DB_NAME ?? "xcore_web",
  );
}
