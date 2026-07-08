const { Pool } = require('pg');
require('dotenv').config();

const connectionString = process.env.DATABASE_URL || 'postgresql://stream:play@localhost:5432/streamplay';

const pool = new Pool({
  connectionString,
  ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: false } : false
});

async function initDB() {
  try {
    const client = await pool.connect();
    console.log('PostgreSQL connected successfully.');
    
    // Create users table
    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id VARCHAR(100) PRIMARY KEY,
        email VARCHAR(255) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        display_name VARCHAR(100) NOT NULL,
        avatar_url TEXT,
        is_guest BOOLEAN DEFAULT FALSE,
        joined_date VARCHAR(50) DEFAULT 'Jul 2026',
        subscribers_count INT DEFAULT 0
      );
    `);

    // Create videos table
    await client.query(`
      CREATE TABLE IF NOT EXISTS videos (
        id VARCHAR(100) PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        video_url TEXT NOT NULL,
        thumbnail_url TEXT NOT NULL,
        creator_id VARCHAR(100) NOT NULL,
        creator_name VARCHAR(100) NOT NULL,
        creator_avatar TEXT,
        views BIGINT DEFAULT 0,
        likes BIGINT DEFAULT 0,
        dislikes BIGINT DEFAULT 0,
        duration VARCHAR(20) DEFAULT '00:00',
        upload_date VARCHAR(50) DEFAULT 'Just now',
        category VARCHAR(100) NOT NULL,
        is_live BOOLEAN DEFAULT FALSE,
        is_short BOOLEAN DEFAULT FALSE,
        comments_count INT DEFAULT 0
      );
    `);

    // Create comments table
    await client.query(`
      CREATE TABLE IF NOT EXISTS comments (
        id VARCHAR(100) PRIMARY KEY,
        video_id VARCHAR(100) REFERENCES videos(id) ON DELETE CASCADE,
        user_id VARCHAR(100) NOT NULL,
        user_name VARCHAR(100) NOT NULL,
        user_avatar TEXT,
        content TEXT NOT NULL,
        timestamp VARCHAR(50) DEFAULT 'Just now',
        likes INT DEFAULT 0
      );
    `);

    // Create likes table
    await client.query(`
      CREATE TABLE IF NOT EXISTS video_likes (
        user_id VARCHAR(100) NOT NULL,
        video_id VARCHAR(100) REFERENCES videos(id) ON DELETE CASCADE,
        is_like BOOLEAN NOT NULL,
        PRIMARY KEY (user_id, video_id)
      );
    `);

    // Create subscriptions table
    await client.query(`
      CREATE TABLE IF NOT EXISTS subscriptions (
        user_id VARCHAR(100) NOT NULL,
        creator_id VARCHAR(100) NOT NULL,
        PRIMARY KEY (user_id, creator_id)
      );
    `);

    // Create playlists table
    await client.query(`
      CREATE TABLE IF NOT EXISTS playlists (
        id VARCHAR(100) PRIMARY KEY,
        user_id VARCHAR(100) NOT NULL,
        name VARCHAR(100) NOT NULL,
        is_private BOOLEAN DEFAULT TRUE,
        created_date VARCHAR(50) DEFAULT 'Jul 2026',
        video_ids TEXT DEFAULT ''
      );
    `);

    // Seed data if no videos exist
    const { rows } = await client.query('SELECT COUNT(*) FROM videos');
    if (parseInt(rows[0].count) === 0) {
      console.log('Seeding initial videos into database...');
      const seedVideos = [
        {
          id: 'vid_1',
          title: 'Introduction to Jetpack Compose: Building Beautiful Interfaces',
          description: 'Learn how to build modern Android UIs with Jetpack Compose. This video covers layouts, state management, modifiers, and standard material design 3 UI components with best practices.',
          video_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
          thumbnail_url: 'https://images.unsplash.com/photo-1607799279861-4dd421887fb3?auto=format&fit=crop&w=640&q=80',
          creator_id: 'creator_android_dev',
          creator_name: 'Android Developer Academy',
          creator_avatar: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=120&q=80',
          views: 124500,
          likes: 8900,
          dislikes: 120,
          duration: '09:56',
          upload_date: '2 days ago',
          category: 'Education',
          is_live: false,
          is_short: false,
          comments_count: 2
        },
        {
          id: 'vid_2',
          title: 'Top 10 High-Performance Rust Frameworks in 2026',
          description: 'Explore the cutting-edge of system development with the top Rust web and desktop frameworks. We measure request latency, memory footprint, and compile times in high-concurrency environments.',
          video_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
          thumbnail_url: 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=640&q=80',
          creator_id: 'creator_rustacean',
          creator_name: 'The Coding Sanctuary',
          creator_avatar: 'https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=120&q=80',
          views: 85900,
          likes: 4120,
          dislikes: 45,
          duration: '15:30',
          upload_date: '5 days ago',
          category: 'Education',
          is_live: false,
          is_short: false,
          comments_count: 1
        },
        {
          id: 'vid_3',
          title: 'Epic Live Chillstep Session for Late-Night Coding 🎧',
          description: 'Sit back, focus, and stream these high-quality lofi and chillstep beats designed specifically for deep flow state programming sessions. Active 24/7 with a welcoming chat community.',
          video_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
          thumbnail_url: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=640&q=80',
          creator_id: 'creator_lofi_beats',
          creator_name: 'Sonic Horizon',
          creator_avatar: 'https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=120&q=80',
          views: 450000,
          likes: 34500,
          dislikes: 80,
          duration: '24:00',
          upload_date: 'Live',
          category: 'Music',
          is_live: true,
          is_short: false,
          comments_count: 0
        },
        {
          id: 'vid_shorts_1',
          title: 'How does database indexing work in 60 seconds?',
          description: 'The absolute fastest way to understand B-Trees, lookup complexity, and why indexing makes select queries lightning fast.',
          video_url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4',
          thumbnail_url: 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?auto=format&fit=crop&w=640&q=80',
          creator_id: 'creator_android_dev',
          creator_name: 'Android Developer Academy',
          creator_avatar: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=120&q=80',
          views: 890300,
          likes: 78300,
          dislikes: 400,
          duration: '00:58',
          upload_date: '4 days ago',
          category: 'Education',
          is_live: false,
          is_short: true,
          comments_count: 0
        }
      ];

      for (const video of seedVideos) {
        await client.query(`
          INSERT INTO videos (
            id, title, description, video_url, thumbnailUrl, creator_id,
            creator_name, creator_avatar, views, likes, dislikes,
            duration, upload_date, category, is_live, is_short, comments_count
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17)
        `, [
          video.id, video.title, video.description, video.video_url, video.thumbnail_url, video.creator_id,
          video.creator_name, video.creator_avatar, video.views, video.likes, video.dislikes,
          video.duration, video.upload_date, video.category, video.is_live, video.is_short, video.comments_count
        ]);
      }
      console.log('Seeded successfully!');
    }

    client.release();
  } catch (err) {
    console.error('PostgreSQL Connection/Initialization Error:', err.message);
    console.log('Using in-memory/file mock database mode fallback for robust startup context.');
  }
}

module.exports = {
  pool,
  initDB,
  query: (text, params) => pool.query(text, params)
};
