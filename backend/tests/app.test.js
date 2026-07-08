const request = require('supertest');
const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');

// Mock database module with self-contained bcrypt logic and mock-prefixed variables
jest.mock('../config/db', () => {
  const bcrypt = require('bcryptjs');
  const mockPasswordHash = bcrypt.hashSync('password123', 10);
  
  return {
    query: jest.fn().mockImplementation((sql, params) => {
      if (sql.includes('SELECT * FROM users WHERE email')) {
        const email = params[0];
        if (email === 'test@example.com') {
          return Promise.resolve({
            rows: [{ id: 'user_123', email: 'test@example.com', password_hash: mockPasswordHash, display_name: 'Tester' }]
          });
        }
        return Promise.resolve({ rows: [] }); // not found
      }
      if (sql.includes('INSERT INTO users')) {
        return Promise.resolve({ rows: [] });
      }
      if (sql.includes('SELECT * FROM videos')) {
        return Promise.resolve({
          rows: [
            { id: 'vid_1', title: 'Test Video', description: 'Test', video_url: 'http://test.com', thumbnail_url: 'http://test.jpg', creator_id: 'creator_1', creator_name: 'Creator' }
          ]
        });
      }
      return Promise.resolve({ rows: [] });
    })
  };
});

// Mock Cache
jest.mock('../config/redis', () => ({
  getCache: jest.fn().mockResolvedValue(null),
  setCache: jest.fn().mockResolvedValue(true)
}));

// Mock Firebase & S3 & RTMP
jest.mock('../utils/firebase', () => ({
  sendPushNotification: jest.fn().mockResolvedValue(true),
  subscribeToTopic: jest.fn().mockResolvedValue(true),
  isConfigured: () => true
}));
jest.mock('../utils/s3', () => ({
  uploadFile: jest.fn().mockResolvedValue('http://s3.com/file'),
  uploadHLSDirectory: jest.fn().mockResolvedValue(true),
  isConfigured: () => true
}));
jest.mock('../utils/queue', () => ({
  addJob: jest.fn().mockResolvedValue('job_123'),
  startQueueWorker: jest.fn(),
  stopQueueWorker: jest.fn()
}));

// Load routes & auth middleware
const authRouter = require('../routes/auth');
const videoRouter = require('../routes/videos');
const paymentsRouter = require('../routes/payments');
const notificationsRouter = require('../routes/notifications');
const { metricsMiddleware, exposeMetrics } = require('../middleware/metrics');
const { JWT_SECRET } = require('../middleware/auth');

// Create test app instance
const app = express();
app.use(cors());
app.use(express.json());

// Mount routes
app.get('/metrics', exposeMetrics);
app.use(metricsMiddleware);
app.use('/api/auth', authRouter);
app.use('/api/videos', videoRouter);
app.use('/api/payments', paymentsRouter);
app.use('/api/notifications', notificationsRouter);

describe('StreamPlay API End-To-End Module Testing', () => {

  // Test Metrics
  test('GET /metrics should expose Prometheus system telemetry', async () => {
    const res = await request(app).get('/metrics');
    expect(res.statusCode).toBe(200);
    expect(res.headers['content-type']).toContain('text/plain');
    expect(res.text).toContain('streamplay_api_');
  });

  // Test Authentication Router
  test('POST /api/auth/register should create a new user profile', async () => {
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'new@example.com', password: 'password123', displayName: 'Jane Doe' });
    
    expect(res.statusCode).toBe(201);
    expect(res.body).toHaveProperty('token');
    expect(res.body.user).toHaveProperty('email', 'new@example.com');
  });

  test('POST /api/auth/login should authenticate credentials & return session JWT', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'test@example.com', password: 'password123' });
    
    expect(res.statusCode).toBe(200);
    expect(res.body).toHaveProperty('token');
  });

  // Test Video Feed Router
  test('GET /api/videos should return list of active videos', async () => {
    const res = await request(app).get('/api/videos');
    expect(res.statusCode).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    expect(res.body[0]).toHaveProperty('title', 'Test Video');
  });

  // Test Payments Router
  test('POST /api/payments/checkout should return sandbox payment url if stripe not configured', async () => {
    const token = jwt.sign({ id: 'user_123', email: 'test@example.com' }, JWT_SECRET);
    
    const res = await request(app)
      .post('/api/payments/checkout')
      .set('Authorization', `Bearer ${token}`)
      .send();

    expect(res.statusCode).toBe(200);
    expect(res.body).toHaveProperty('url');
    expect(res.body.url).toContain('/sandbox-payment-success');
  });

  // Test Push Notifications Router
  test('POST /api/notifications/register should register FCM device tokens successfully', async () => {
    const token = jwt.sign({ id: 'user_123', email: 'test@example.com' }, JWT_SECRET);
    
    const res = await request(app)
      .post('/api/notifications/register')
      .set('Authorization', `Bearer ${token}`)
      .send({ fcmToken: 'fcm_token_device_abc_123' });

    expect(res.statusCode).toBe(200);
    expect(res.body).toHaveProperty('message', 'Device token registered successfully.');
  });
});
