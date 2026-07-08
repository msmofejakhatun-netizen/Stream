const { createClient } = require('redis');
require('dotenv').config();

const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';

const client = createClient({
  url: redisUrl
});

client.on('error', (err) => {
  console.error('Redis Client Error:', err.message);
});

client.on('connect', () => {
  console.log('Redis connected successfully.');
});

let isRedisConnected = false;
const fallbackCache = new Map();

async function initRedis() {
  try {
    if (process.env.DISABLE_REDIS === 'true') {
      console.log('Redis is disabled by config. Using local memory cache.');
      return;
    }
    await client.connect();
    isRedisConnected = true;
  } catch (err) {
    console.error('Redis Connection Failed. Falling back to in-memory caching.', err.message);
    isRedisConnected = false;
  }
}

async function getCache(key) {
  if (isRedisConnected) {
    try {
      const val = await client.get(key);
      return val ? JSON.parse(val) : null;
    } catch (err) {
      console.error('Redis Get Error:', err.message);
    }
  }
  return fallbackCache.get(key) || null;
}

async function setCache(key, value, ttlSeconds = 120) {
  if (isRedisConnected) {
    try {
      await client.set(key, JSON.stringify(value), {
        EX: ttlSeconds
      });
      return;
    } catch (err) {
      console.error('Redis Set Error:', err.message);
    }
  }
  fallbackCache.set(key, value);
  setTimeout(() => {
    fallbackCache.delete(key);
  }, ttlSeconds * 1000);
}

module.exports = {
  client,
  initRedis,
  getCache,
  setCache
};
