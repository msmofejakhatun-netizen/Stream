const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');
const db = require('../config/db');
const cache = require('../config/redis');
const { optionalAuthenticateToken, authenticateToken } = require('../middleware/auth');
const queue = require('../utils/queue');
const logger = require('../utils/logger');

// Configure Multer for secure disk storage of uploads
const tempStorage = multer.diskStorage({
  destination: (req, file, cb) => {
    const tempDir = path.join(__dirname, '..', 'public', 'uploads', 'temp');
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true });
    }
    cb(null, tempDir);
  },
  filename: (req, file, cb) => {
    cb(null, `raw_${uuidv4()}_${file.originalname}`);
  }
});

const upload = multer({
  storage: tempStorage,
  limits: { fileSize: 100 * 1024 * 1024 }, // 100MB max upload size
  fileFilter: (req, file, cb) => {
    const filetypes = /mp4|mov|avi|mkv/;
    const mimetype = filetypes.test(file.mimetype);
    const extname = filetypes.test(path.extname(file.originalname).toLowerCase());
    if (mimetype && extname) {
      return cb(null, true);
    }
    cb(new Error('Only video files (MP4, MOV, AVI, MKV) are supported.'));
  }
});

// Cache invalidation helper
async function invalidateVideoCache() {
  // Invalidate list cache keys
  // Since keys are specific, we can let them expire, or flush
  try {
    const keys = ['videos_all', 'videos_trending', 'videos_recommended', 'videos_shorts'];
    for (const key of keys) {
      // Invalidate
      cache.setCache(key, null, 1);
    }
  } catch (err) {
    console.error('Cache invalidation failed:', err.message);
  }
}

// 1. Get Video Feed (Trending, Recommended, or Specific Category)
router.get('/', optionalAuthenticateToken, async (req, res) => {
  const { category, isShort, query } = req.query;
  const cacheKey = `videos_cat_${category || 'all'}_short_${isShort || 'false'}_q_${query || 'none'}`;

  // Try fetching from Redis cache
  try {
    const cachedData = await cache.getCache(cacheKey);
    if (cachedData) {
      console.log('Serving video feed from Redis cache...');
      return res.json(cachedData);
    }
  } catch (err) {
    console.warn('Redis read failed in route, loading from DB:', err.message);
  }

  try {
    let sql = 'SELECT * FROM videos WHERE 1=1';
    const params = [];

    if (isShort === 'true') {
      sql += ' AND is_short = true';
    } else if (category === 'Trending') {
      sql += ' AND is_short = false ORDER BY views DESC';
    } else if (category === 'Recommended') {
      sql += ' AND is_short = false ORDER BY id ASC';
    } else if (category) {
      params.push(category);
      sql += ` AND is_short = false AND LOWER(category) = LOWER($${params.length})`;
    } else {
      sql += ' AND is_short = false';
    }

    if (query) {
      params.push(`%${query}%`);
      sql += ` AND (LOWER(title) LIKE LOWER($${params.length}) OR LOWER(description) LIKE LOWER($${params.length}) OR LOWER(creator_name) LIKE LOWER($${params.length}))`;
    }

    const { rows } = await db.query(sql, params);

    // Map fields to match Kotlin models (camelCase)
    const videos = rows.map(row => ({
      id: row.id,
      title: row.title,
      description: row.description,
      videoUrl: row.video_url,
      thumbnailUrl: row.thumbnail_url,
      creatorId: row.creator_id,
      creatorName: row.creator_name,
      creatorAvatar: row.creator_avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80',
      views: parseInt(row.views || 0),
      likes: parseInt(row.likes || 0),
      dislikes: parseInt(row.dislikes || 0),
      duration: row.duration,
      uploadDate: row.upload_date,
      category: row.category,
      isLive: !!row.is_live,
      isShort: !!row.is_short,
      commentsCount: parseInt(row.comments_count || 0),
      shareUrl: `https://streamplay.aistudio.com/v/${row.id}`
    }));

    // Cache the result for 120 seconds
    await cache.setCache(cacheKey, videos, 120);

    res.json(videos);
  } catch (err) {
    res.status(500).json({ error: 'Database query failed: ' + err.message });
  }
});

// 2. Get Video by ID
router.get('/:id', optionalAuthenticateToken, async (req, res) => {
  const { id } = req.params;
  try {
    const { rows } = await db.query('SELECT * FROM videos WHERE id = $1', [id]);
    if (rows.length === 0) {
      return res.status(404).json({ error: 'Video not found' });
    }

    const row = rows[0];
    
    // Increment view count asynchronously
    db.query('UPDATE videos SET views = views + 1 WHERE id = $1', [id]).catch(err => {
      console.error('Async view increment failed:', err.message);
    });

    res.json({
      id: row.id,
      title: row.title,
      description: row.description,
      videoUrl: row.video_url,
      thumbnailUrl: row.thumbnail_url,
      creatorId: row.creator_id,
      creatorName: row.creator_name,
      creatorAvatar: row.creator_avatar,
      views: parseInt(row.views || 0) + 1,
      likes: parseInt(row.likes || 0),
      dislikes: parseInt(row.dislikes || 0),
      duration: row.duration,
      uploadDate: row.upload_date,
      category: row.category,
      isLive: !!row.is_live,
      isShort: !!row.is_short,
      commentsCount: parseInt(row.comments_count || 0),
      shareUrl: `https://streamplay.aistudio.com/v/${row.id}`
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to retrieve video: ' + err.message });
  }
});

// 3. Upload Video & Transcode Asynchronously
router.post('/upload', authenticateToken, upload.single('video'), async (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No video file provided' });
  }

  const { title, description, category, isShort } = req.body;
  if (!title || !category) {
    // Cleanup temporary uploaded file
    try { fs.unlinkSync(req.file.path); } catch (e) {}
    return res.status(400).json({ error: 'Title and category are required fields' });
  }

  const videoId = 'vid_' + uuidv4().substring(0, 8);
  const isShortBool = isShort === 'true';

  try {
    logger.info(`Asynchronous upload received for video: ${title}, temp path: ${req.file.path}`);

    // Get creator details
    const creatorId = req.user.id;
    const creatorName = req.user.displayName || 'Creator';
    const creatorAvatar = req.user.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80';

    // Insert placeholder video so the user sees it immediately in their channel dashboard with "Transcoding" duration
    const host = req.get('host');
    const protocol = req.protocol;
    const baseServerUrl = `${protocol}://${host}`;
    const processingVideoUrl = `${baseServerUrl}/uploads/processing`;
    const processingThumbnailUrl = `https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=640&q=80`;

    await db.query(`
      INSERT INTO videos (
        id, title, description, video_url, thumbnailUrl, creator_id,
        creator_name, creator_avatar, views, likes, dislikes,
        duration, upload_date, category, is_live, is_short, comments_count
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17)
    `, [
      videoId, title, description || '', processingVideoUrl, processingThumbnailUrl, creatorId,
      creatorName, creatorAvatar, 0, 0, 0,
      'Transcoding...', 'Just now', category, false, isShortBool, 0
    ]);

    // Enqueue transcoding job to the single-threaded processor queue
    await queue.addJob({
      videoId,
      title,
      description,
      category,
      isShort: isShortBool,
      inputPath: req.file.path,
      creatorId,
      creatorName,
      creatorAvatar
    });

    await invalidateVideoCache();

    res.status(202).json({
      message: 'Video upload accepted successfully. Transcoding has started in the background.',
      id: videoId,
      title,
      description,
      videoUrl: processingVideoUrl,
      thumbnailUrl: processingThumbnailUrl,
      creatorId,
      creatorName,
      creatorAvatar,
      views: 0,
      likes: 0,
      dislikes: 0,
      duration: 'Transcoding...',
      uploadDate: 'Just now',
      category,
      isLive: false,
      isShort: isShortBool,
      commentsCount: 0,
      shareUrl: `https://streamplay.aistudio.com/v/${videoId}`
    });
  } catch (err) {
    // Cleanup raw uploaded file on error
    try { fs.unlinkSync(req.file.path); } catch (e) {}
    logger.error(`Failed to register or enqueue upload: ${err.message}`);
    res.status(500).json({ error: 'Video upload queuing failed: ' + err.message });
  }
});

// 4. Like / Dislike a Video
router.post('/:id/like', authenticateToken, async (req, res) => {
  const { id } = req.params;
  const { isLike } = req.body; // boolean
  const userId = req.user.id;

  if (isLike === undefined) {
    return res.status(400).json({ error: 'isLike body parameter (boolean) is required' });
  }

  try {
    // Update or insert like state
    await db.query(`
      INSERT INTO video_likes (user_id, video_id, is_like)
      VALUES ($1, $2, $3)
      ON CONFLICT (user_id, video_id) DO UPDATE SET is_like = EXCLUDED.is_like
    `, [userId, id, isLike]);

    // Recalculate and update totals on video row
    const likeCountResult = await db.query('SELECT COUNT(*) FROM video_likes WHERE video_id = $1 AND is_like = true', [id]);
    const dislikeCountResult = await db.query('SELECT COUNT(*) FROM video_likes WHERE video_id = $1 AND is_like = false', [id]);
    
    const likes = parseInt(likeCountResult.rows[0].count);
    const dislikes = parseInt(dislikeCountResult.rows[0].count);

    await db.query('UPDATE videos SET likes = $1, dislikes = $2 WHERE id = $3', [likes, dislikes, id]);

    res.json({ success: true, likes, dislikes });
  } catch (err) {
    res.status(500).json({ error: 'Failed to update like status: ' + err.message });
  }
});

// 5. Get Comments for Video
router.get('/:id/comments', async (req, res) => {
  const { id } = req.params;
  try {
    const { rows } = await db.query('SELECT * FROM comments WHERE video_id = $1 ORDER BY id DESC', [id]);
    const comments = rows.map(row => ({
      id: row.id,
      videoId: row.video_id,
      userName: row.user_name,
      userAvatar: row.user_avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80',
      content: row.content,
      timestamp: row.timestamp,
      likes: parseInt(row.likes || 0)
    }));
    res.json(comments);
  } catch (err) {
    res.status(500).json({ error: 'Failed to retrieve comments: ' + err.message });
  }
});

// 6. Post Comment
router.post('/:id/comments', authenticateToken, async (req, res) => {
  const { id } = req.params;
  const { content } = req.body;
  if (!content) {
    return res.status(400).json({ error: 'Content is required' });
  }

  const commentId = 'comment_' + uuidv4().substring(0, 6);
  const userName = req.user.displayName || 'Anonymous';
  const userAvatar = req.user.avatarUrl || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80';

  try {
    await db.query(`
      INSERT INTO comments (id, video_id, user_id, user_name, user_avatar, content, timestamp, likes)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
    `, [commentId, id, req.user.id, userName, userAvatar, content, 'Just now', 0]);

    await db.query('UPDATE videos SET comments_count = comments_count + 1 WHERE id = $1', [id]);

    res.status(201).json({
      id: commentId,
      videoId: id,
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

// 7. Get Channel Profile / Info
router.get('/creator/:id', optionalAuthenticateToken, async (req, res) => {
  const { id } = req.params;
  const currentUserId = req.user ? req.user.id : null;

  try {
    // Sum video count
    const videoCountResult = await db.query('SELECT COUNT(*) FROM videos WHERE creator_id = $1', [id]);
    const videoCount = parseInt(videoCountResult.rows[0].count);

    // Check if current user is subscribed
    let isSubscribed = false;
    if (currentUserId) {
      const subCheck = await db.query('SELECT * FROM subscriptions WHERE user_id = $1 AND creator_id = $2', [currentUserId, id]);
      isSubscribed = subCheck.rows.length > 0;
    }

    // Get basic details from videos or users table
    const creatorUser = await db.query('SELECT * FROM users WHERE id = $1', [id]);
    let name = 'Creator';
    let avatar = 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80';
    let subsCount = 1420;

    if (creatorUser.rows.length > 0) {
      name = creatorUser.rows[0].display_name;
      avatar = creatorUser.rows[0].avatar_url;
      subsCount = parseInt(creatorUser.rows[0].subscribers_count || 1420);
    } else {
      // Find from videos as backup
      const videoBackup = await db.query('SELECT creator_name, creator_avatar FROM videos WHERE creator_id = $1 LIMIT 1', [id]);
      if (videoBackup.rows.length > 0) {
        name = videoBackup.rows[0].creator_name;
        avatar = videoBackup.rows[0].creator_avatar;
      }
    }

    res.json({
      id,
      name,
      avatarUrl: avatar,
      bannerUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1280&h=300&q=80',
      subscriberCount: subsCount + (isSubscribed ? 1 : 0),
      videoCount,
      isSubscribed,
      description: `Welcome to the official ${name} channel! Dedicated to high-fidelity tutorial videos, live streams, and developer insights.`,
      totalEarnings: 2450.50,
      monthlyViews: 85900
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch creator channel: ' + err.message });
  }
});

// 8. Subscribe / Unsubscribe Creator
router.post('/creator/:id/subscribe', authenticateToken, async (req, res) => {
  const { id } = req.params;
  const userId = req.user.id;

  try {
    const existing = await db.query('SELECT * FROM subscriptions WHERE user_id = $1 AND creator_id = $2', [userId, id]);
    let isSubscribed = false;

    if (existing.rows.length > 0) {
      // Unsubscribe
      await db.query('DELETE FROM subscriptions WHERE user_id = $1 AND creator_id = $2', [userId, id]);
      await db.query('UPDATE users SET subscribers_count = GREATEST(0, subscribers_count - 1) WHERE id = $1', [id]);
      isSubscribed = false;
    } else {
      // Subscribe
      await db.query('INSERT INTO subscriptions (user_id, creator_id) VALUES ($1, $2)', [userId, id]);
      await db.query('UPDATE users SET subscribers_count = subscribers_count + 1 WHERE id = $1', [id]);
      isSubscribed = true;
    }

    res.json({ success: true, isSubscribed });
  } catch (err) {
    res.status(500).json({ error: 'Subscription toggle failed: ' + err.message });
  }
});

module.exports = router;
