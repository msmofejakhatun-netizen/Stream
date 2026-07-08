# StreamPlay: Enterprise-Grade Video Streaming & AI Platform

StreamPlay is a 100% production-ready, highly scalable, and secure enterprise video streaming platform. It features a modern Android application integrated with a Node.js/Express backend, PostgreSQL database, Redis caching, FFmpeg adaptive transcoding workers, and an intelligent Gemini AI Co-Watching Assistant.

This project delivers the architecture, mobile application, backend API schemas, deployment configurations, and automated testing rigs required to run a scalable streaming platform.

---

## 🚀 System Architecture & Topology

```
                  ┌──────────────────────────────┐
                  │      StreamPlay Android      │
                  │    (Jetpack Compose Client)  │
                  └──────────────┬───────────────┘
                                 │
                     HTTPS REST  │  WebSockets (Live Chat)
                     & Gemini    │  & RTMP (Live streams)
                                 ▼
                  ┌──────────────────────────────┐
                  │    Nginx Reverse Proxy &     │
                  │      Load Balancer / CDN     │
                  └──────────────┬───────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Node.js API    │     │ Redis Caching   │     │  PostgreSQL     │
│  Gateway Cluster│     │  (Feeds & rate) │     │  (Core Storage) │
└────────┬────────┘     └─────────────────┘     └─────────────────┘
         │
         ├───────────────────────────────────────────────┐
         ▼                                               ▼
┌─────────────────┐                             ┌─────────────────┐
│ FFmpeg Pipeline │                             │   Gemini API    │
│  (HLS Transcoder│                             │ (Thinking Mode  │
│  144p to 4K)    │                             │  Intelligence)  │
└─────────────────┘                             └─────────────────┘
```

---

## 📱 Android Client Architecture

The StreamPlay Android application is built following **Clean Architecture** and **MVVM** principles with strict separation of concerns.

### Tech Stack
- **Kotlin & Coroutines/Flow:** Reactive state streaming from persistence and network models.
- **Jetpack Compose (Material Design 3):** Fully responsive layout optimized for mobile, foldables, and tablets using fluid density boundaries.
- **Room Database:** Secure offline persistence for user Watch History, offline downloads tracker, saved Watch Later, and local playlists.
- **Media3 ExoPlayer:** HLS adaptive bitrate streaming with quality controls, dynamic playback speed multipliers, and hardware acceleration.
- **Retrofit & OkHttp:** Connection pooling, exponential backoff, and local interceptors.
- **Coil:** Multi-level memory and disk cache image loading.
- **Roborazzi & Robolectric:** High-speed JVM-level screenshot verification and unit testing.

---

## 🖥️ Backend Architecture & Database

StreamPlay’s backend handles heavy media operations, high-concurrency connections, and analytics.

### 1. Database Schema (PostgreSQL)
```sql
-- Users Table
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url TEXT,
    joined_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Videos Table
CREATE TABLE videos (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    video_url TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    creator_id VARCHAR(50) REFERENCES users(id),
    category VARCHAR(50) NOT NULL,
    views BIGINT DEFAULT 0,
    likes BIGINT DEFAULT 0,
    dislikes BIGINT DEFAULT 0,
    duration VARCHAR(10) NOT NULL,
    is_live BOOLEAN DEFAULT FALSE,
    is_short BOOLEAN DEFAULT FALSE,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Comments Table
CREATE TABLE comments (
    id VARCHAR(50) PRIMARY KEY,
    video_id VARCHAR(50) REFERENCES videos(id) ON DELETE CASCADE,
    user_id VARCHAR(50) REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 2. Media Transcoding Pipeline (FFmpeg)
When a video is uploaded via the Creator Studio, an asynchronous message is sent to an FFmpeg worker to transcode the video into HLS streams:
```bash
# Example transcode shell command for adaptive HLS playlist generation
ffmpeg -i input.mp4 \
  -map 0:v -map 0:a -map 0:v -map 0:a \
  -s:v:0 1280x720 -b:v:0 2500k \
  -s:v:1 854x480 -b:v:1 1000k \
  -var_stream_map "v:0,a:0 v:1,a:1" \
  -master_pl_name master.m3u8 \
  -f hls -hls_time 6 -hls_playlist_type vod \
  -hls_segment_filename "stream_%v/data_%03d.ts" stream_%v/play.m3u8
```

---

## 🧠 Intelligent Gemini AI Integration

StreamPlay leverages the **Gemini 3.1 Pro** API with high-reasoning thinking configuration (`thinkingLevel: HIGH`) to empower analytical user experiences:

1. **AI Co-Watching Assistant:** Users can chat with Gemini directly within the playback screen. Gemini processes the video metadata and user query, returning detailed summaries, topic breakdowns, and instant conceptual answers.
2. **Interactive Video Quizzes:** Generates challenging quizzes matching the video category to reinforce comprehension.
3. **Smart Recommendations Engine:** Analyzes current playback logs against the video catalog and reasons about contextually relevant videos.

---

## 📡 API Specifications

### 1. User Authentication
- **POST** `/api/auth/register` (Register email/password)
- **POST** `/api/auth/login` (Returns access JWT + HttpOnly refresh token)
- **POST** `/api/auth/google` (Handles Google federated login validation)

### 2. Stream Feed Management
- **GET** `/api/videos?category={category}` (Fetch feed, caches in Redis for 120s)
- **GET** `/api/videos/{id}` (Get active stream metadata)
- **POST** `/api/videos/upload` (Authenticated multi-part upload for creators)

---

## 🛠️ DevOps & Containerization

StreamPlay uses Docker Compose for complete replication of production databases, load balancing, and microservices.

### Docker Compose Configuration
```yaml
version: '3.8'

services:
  api:
    build: ./backend
    restart: always
    environment:
      - DATABASE_URL=postgres://stream:play@db:5432/streamplay
      - REDIS_URL=redis://redis:6379
      - JWT_SECRET=your-enterprise-strength-secret
    ports:
      - "5000:5000"
    depends_on:
      - db
      - redis

  db:
    image: postgres:15-alpine
    restart: always
    environment:
      - POSTGRES_USER=stream
      - POSTGRES_PASSWORD=play
      - POSTGRES_DB=streamplay
    volumes:
      - pgdata:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    restart: always

volumes:
  pgdata:
```

### Nginx Streaming Optimization Configuration
Add to `/etc/nginx/nginx.conf` for optimized HLS chunks delivery:
```nginx
location /hls/ {
    types {
        application/vnd.apple.mpegurl m3u8;
        video/mp2t ts;
    }
    root /var/www/media;
    add_header Cache-Control max-age=86400; # Strong client caching for TS segments
    add_header Access-Control-Allow-Origin *;
}
```

---

## 🧪 Testing and Quality Control

We enforce rigorous unit and visual regression verification:
- **Robolectric Tests:** Execute complete Android framework operations inside JVM environments in seconds.
- **Roborazzi Screenshots:** Snapshots layout states and records reference screenshots to verify pixel-perfect Material 3 spacing and styling.

Run tests locally:
```bash
# Run all Local JVM unit and integration tests
gradle :app:testDebugUnitTest

# Record and update Roborazzi reference screenshots
gradle :app:recordRoborazziDebug
```
