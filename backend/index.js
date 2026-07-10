const express = require('express');
const cors = require('cors');
const path = require('path');
const helmet = require('helmet');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
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

// Security and production-ready middleware
app.use(helmet({
  crossOriginResourcePolicy: false,
  contentSecurityPolicy: false
}));
app.use(compression());

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 1000, // Limit each IP to 1000 requests per windowMs
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests from this IP, please try again later.' }
});
app.use('/api/', limiter);

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

// Top-Level Alias API Routes for Complete Android Compatibility
const { authenticateToken } = require('./middleware/auth');
const db = require('./config/db');
const { v4: uuidv4 } = require('uuid');

app.post('/api/comments', authenticateToken, async (req, res) => {
  const { videoId, content } = req.body;
  if (!videoId || !content) {
    return res.status(400).json({ error: 'videoId and content are required' });
  }
  const commentId = 'comment_' + uuidv4().substring(0, 6);
  const userName = req.user.displayName || 'Anonymous';
  const userAvatar = req.user.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80';

  try {
    await db.query(`
      INSERT INTO comments (id, video_id, user_id, user_name, user_avatar, content, timestamp, likes)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
    `, [commentId, videoId, req.user.id, userName, userAvatar, content, 'Just now', 0]);

    await db.query('UPDATE videos SET comments_count = comments_count + 1 WHERE id = $1', [videoId]);

    res.status(201).json({
      id: commentId,
      videoId: videoId,
      userName,
      userAvatar,
      content,
      timestamp: 'Just now',
      likes: 0
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to post comment: ' + err.message });
  }
});

app.post('/api/likes', authenticateToken, async (req, res) => {
  const { videoId, isLike } = req.body;
  const userId = req.user.id;

  if (!videoId || isLike === undefined) {
    return res.status(400).json({ error: 'videoId and isLike (boolean) are required' });
  }

  try {
    await db.query(`
      INSERT INTO video_likes (user_id, video_id, is_like)
      VALUES ($1, $2, $3)
      ON CONFLICT (user_id, video_id) DO UPDATE SET is_like = EXCLUDED.is_like
    `, [userId, videoId, isLike]);

    const likeCountResult = await db.query('SELECT COUNT(*) FROM video_likes WHERE video_id = $1 AND is_like = true', [videoId]);
    const dislikeCountResult = await db.query('SELECT COUNT(*) FROM video_likes WHERE video_id = $1 AND is_like = false', [videoId]);
    
    const likes = parseInt(likeCountResult.rows[0].count);
    const dislikes = parseInt(dislikeCountResult.rows[0].count);

    await db.query('UPDATE videos SET likes = $1, dislikes = $2 WHERE id = $3', [likes, dislikes, videoId]);

    res.json({ success: true, likes, dislikes });
  } catch (err) {
    res.status(500).json({ error: 'Failed to update like status: ' + err.message });
  }
});

app.post('/api/subscriptions', authenticateToken, async (req, res) => {
  const { creatorId } = req.body;
  const userId = req.user.id;

  if (!creatorId) {
    return res.status(400).json({ error: 'creatorId is required' });
  }

  try {
    const existing = await db.query('SELECT * FROM subscriptions WHERE user_id = $1 AND creator_id = $2', [userId, creatorId]);
    let isSubscribed = false;

    if (existing.rows.length > 0) {
      await db.query('DELETE FROM subscriptions WHERE user_id = $1 AND creator_id = $2', [userId, creatorId]);
      await db.query('UPDATE users SET subscribers_count = GREATEST(0, subscribers_count - 1) WHERE id = $1', [creatorId]);
      isSubscribed = false;
    } else {
      await db.query('INSERT INTO subscriptions (user_id, creator_id) VALUES ($1, $2)', [userId, creatorId]);
      await db.query('UPDATE users SET subscribers_count = subscribers_count + 1 WHERE id = $1', [creatorId]);
      isSubscribed = true;
    }

    res.json({ success: true, isSubscribed });
  } catch (err) {
    res.status(500).json({ error: 'Subscription toggle failed: ' + err.message });
  }
});

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
