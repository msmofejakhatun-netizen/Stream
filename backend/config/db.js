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

    // Create database indexes to optimize queries
    await client.query('CREATE INDEX IF NOT EXISTS idx_videos_is_short ON videos(is_short);');
    await client.query('CREATE INDEX IF NOT EXISTS idx_videos_is_short ON videos(is_short);');
    await client.query('CREATE INDEX IF NOT EXISTS idx_videos_category ON videos(category);');
    await client.query('CREATE INDEX IF NOT EXISTS idx_videos_creator_id ON videos(creator_id);');
    await client.query('CREATE INDEX IF NOT EXISTS idx_comments_video_id ON comments(video_id);');
    await client.query('CREATE INDEX IF NOT EXISTS idx_video_likes_video_id ON video_likes(video_id);');
    await client.query('CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id_creator_id ON subscriptions(user_id, creator_id);');

    // Alter users table to support admin, ban, verified, premium
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(50) DEFAULT \'User\';');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT FALSE;');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;');
    await client.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS is_premium BOOLEAN DEFAULT FALSE;');

    // Alter videos table to support published/unpublished, featured, trending, scheduled_time, pinned
    await client.query('ALTER TABLE videos ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT TRUE;');
    await client.query('ALTER TABLE videos ADD COLUMN IF NOT EXISTS is_featured BOOLEAN DEFAULT FALSE;');
    await client.query('ALTER TABLE videos ADD COLUMN IF NOT EXISTS is_trending BOOLEAN DEFAULT FALSE;');
    await client.query('ALTER TABLE videos ADD COLUMN IF NOT EXISTS scheduled_time VARCHAR(100);');
    await client.query('ALTER TABLE videos ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN DEFAULT FALSE;');

    // Create categories table
    await client.query(`
      CREATE TABLE IF NOT EXISTS categories (
        id VARCHAR(100) PRIMARY KEY,
        name VARCHAR(100) UNIQUE NOT NULL,
        genre VARCHAR(100) DEFAULT 'General',
        language VARCHAR(100) DEFAULT 'English'
      );
    `);

    // Create banners table
    await client.query(`
      CREATE TABLE IF NOT EXISTS banners (
        id VARCHAR(100) PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        image_url TEXT NOT NULL,
        type VARCHAR(50) NOT NULL, -- 'home', 'shorts', 'splash', 'carousel'
        target_video_id VARCHAR(100),
        is_active BOOLEAN DEFAULT TRUE
      );
    `);

    // Create app_settings table
    await client.query(`
      CREATE TABLE IF NOT EXISTS app_settings (
        key VARCHAR(100) PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);

    // Create audit_logs table
    await client.query(`
      CREATE TABLE IF NOT EXISTS audit_logs (
        id VARCHAR(100) PRIMARY KEY,
        user_id VARCHAR(100),
        action VARCHAR(255) NOT NULL,
        details TEXT,
        ip_address VARCHAR(50),
        timestamp VARCHAR(100) DEFAULT 'Just now'
      );
    `);

    // Create monetization_settings table
    await client.query(`
      CREATE TABLE IF NOT EXISTS monetization_settings (
        key VARCHAR(100) PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);

    // Create notifications table
    await client.query(`
      CREATE TABLE IF NOT EXISTS notifications (
        id VARCHAR(100) PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        message TEXT NOT NULL,
        type VARCHAR(50) DEFAULT 'broadcast', -- 'broadcast', 'push', 'scheduled'
        target_user_id VARCHAR(100),
        scheduled_time VARCHAR(100),
        is_sent BOOLEAN DEFAULT FALSE,
        created_at VARCHAR(100)
      );
    `);

    // Seed default categories if none exist
    const catCheck = await client.query('SELECT COUNT(*) FROM categories');
    if (parseInt(catCheck.rows[0].count) === 0) {
      const defaultCategories = [
        { id: 'cat_edu', name: 'Education', genre: 'Tech', language: 'English' },
        { id: 'cat_mus', name: 'Music', genre: 'Lofi', language: 'English' },
        { id: 'cat_ent', name: 'Entertainment', genre: 'Comedy', language: 'English' },
        { id: 'cat_tech', name: 'Technology', genre: 'Coding', language: 'English' }
      ];
      for (const cat of defaultCategories) {
        await client.query('INSERT INTO categories (id, name, genre, language) VALUES ($1, $2, $3, $4)', [cat.id, cat.name, cat.genre, cat.language]);
      }
    }

    // Seed default settings if none exist
    const settingsCheck = await client.query('SELECT COUNT(*) FROM app_settings');
    if (parseInt(settingsCheck.rows[0].count) === 0) {
      await client.query("INSERT INTO app_settings (key, value) VALUES ('app_name', 'StreamPlay OTT')");
      await client.query("INSERT INTO app_settings (key, value) VALUES ('logo_url', 'https://pub-streamplay.r2.dev/logo.png')");
      await client.query("INSERT INTO app_settings (key, value) VALUES ('privacy_policy', 'Our Privacy Policy handles your personal data with care.')");
      await client.query("INSERT INTO app_settings (key, value) VALUES ('terms_conditions', 'Terms of Service governs use of the StreamPlay application.')");
      await client.query("INSERT INTO app_settings (key, value) VALUES ('maintenance_mode', 'false')");
    }

    // Seed default monetization settings if none exist
    const monCheck = await client.query('SELECT COUNT(*) FROM monetization_settings');
    if (parseInt(monCheck.rows[0].count) === 0) {
      await client.query("INSERT INTO monetization_settings (key, value) VALUES ('admob_app_id', 'ca-app-pub-3940256099942544~3347511713')");
      await client.query("INSERT INTO monetization_settings (key, value) VALUES ('google_ima_enabled', 'true')");
      await client.query("INSERT INTO monetization_settings (key, value) VALUES ('vast_ads_url', 'https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319075/external/single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator=')");
    }

    // Seed Super Admin if none exists
    const adminCheck = await client.query("SELECT COUNT(*) FROM users WHERE email = 'admin@streamplay.com'");
    if (parseInt(adminCheck.rows[0].count) === 0) {
      const bcrypt = require('bcryptjs');
      const hash = await bcrypt.hash('admin123', 10);
      await client.query(`
        INSERT INTO users (id, email, password_hash, display_name, avatar_url, is_guest, joined_date, subscribers_count, role)
        VALUES ('u_admin', 'admin@streamplay.com', $1, 'Super Admin', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80', false, 'Jul 2026', 0, 'SuperAdmin')
      `, [hash]);
      console.log('Super Admin user seeded successfully! (admin@streamplay.com / admin123)');
    }

    // Seed data if no videos exist
    const { rows } = await client.query('SELECT COUNT(*) FROM videos');
    if (parseInt(rows[0].count) === 0) {
      console.log('Seeding initial videos into database...');
      
      const r2PublicUrl = process.env.R2_PUBLIC_URL || 'https://pub-streamplay.r2.dev';
      const cleanR2Url = r2PublicUrl.endsWith('/') ? r2PublicUrl.slice(0, -1) : r2PublicUrl;

      const seedVideos = [
        {
          id: 'vid_1',
          title: 'Introduction to Jetpack Compose: Building Beautiful Interfaces',
          description: 'Learn how to build modern Android UIs with Jetpack Compose. This video covers layouts, state management, modifiers, and standard material design 3 UI components with best practices.',
          video_url: `${cleanR2Url}/sample/BigBuckBunny.mp4`,
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
          video_url: `${cleanR2Url}/sample/ElephantsDream.mp4`,
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
          video_url: `${cleanR2Url}/sample/ForBiggerBlazes.mp4`,
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
          video_url: `${cleanR2Url}/sample/WeAreGoingOnBullrun.mp4`,
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
            id, title, description, video_url, thumbnail_url, creator_id,
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
