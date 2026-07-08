const express = require('express');
const cors = require('cors');
const path = require('path');
require('dotenv').config();

const { initDB } = require('./config/db');
const { initRedis } = require('./config/redis');
const logger = require('./utils/logger');

// Routers
const authRouter = require('./routes/auth');
const videoRouter = require('./routes/videos');
const aiRouter = require('./routes/ai');
const paymentsRouter = require('./routes/payments');
const notificationsRouter = require('./routes/notifications');

// Middleware
const { metricsMiddleware, exposeMetrics } = require('./middleware/metrics');

const app = express();
const PORT = process.env.PORT || 5000;

// Expose metrics route before general logging & body parsing
app.get('/metrics', exposeMetrics);
app.use(metricsMiddleware);

// Enable wide-open CORS for local streaming emulator context
app.use(cors());

// Configure global json parser with raw body capture for secure Stripe Webhooks
app.use(express.json({
  verify: (req, res, buf) => {
    if (req.originalUrl.startsWith('/api/payments/webhook')) {
      req.rawBody = buf;
    }
  }
}));
app.use(express.urlencoded({ extended: true }));

// Serve uploaded video and thumbnail static files
const publicDir = path.join(__dirname, 'public');
app.use(express.static(publicDir));
app.use('/uploads', express.static(path.join(publicDir, 'uploads')));

// Mount API routes
app.use('/api/auth', authRouter);
app.use('/api/videos', videoRouter);
app.use('/api/ai', aiRouter);
app.use('/api/payments', paymentsRouter);
app.use('/api/notifications', notificationsRouter);

// Base route
app.get('/', (req, res) => {
  res.json({
    name: 'StreamPlay Enterprise Video Streaming API',
    version: '1.0.0',
    status: 'ONLINE',
    time: new Date().toISOString()
  });
});

let server = null;
const queue = require('./utils/queue');
const live = require('./utils/live');

// Start-up function
async function startServer() {
  logger.info('Initializing StreamPlay Backend Services...');
  
  // Connect database and cache
  await initDB();
  await initRedis();

  // Start background workers
  queue.startQueueWorker();
  live.startLiveServer();

  server = app.listen(PORT, '0.0.0.0', () => {
    logger.info(`====================================================`);
    logger.info(` StreamPlay API Server listening on 0.0.0.0:${PORT}`);
    logger.info(` Static folder: ${publicDir}`);
    logger.info(`====================================================`);
  });
}

// Graceful shutdown handling
function gracefulShutdown() {
  logger.info('Starting graceful teardown of StreamPlay Backend...');
  
  if (server) {
    server.close(() => {
      logger.info('HTTP server closed.');
    });
  }

  // Stop background worker polling
  queue.stopQueueWorker();
  logger.info('Queue worker terminated.');

  // Stop live streaming services
  live.stopLiveServer();
  logger.info('RTMP Streaming services stopped.');

  setTimeout(() => {
    logger.info('Teardown complete. Exiting.');
    process.exit(0);
  }, 1000);
}

process.on('SIGTERM', gracefulShutdown);
process.on('SIGINT', gracefulShutdown);

startServer();
