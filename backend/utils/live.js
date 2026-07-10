const NodeMediaServer = require('node-media-server');
const path = require('path');
const db = require('../config/db');
const logger = require('./logger');
const { sendPushNotification } = require('./firebase');

const config = {
  rtmp: {
    port: 1935,
    chunk_size: 60000,
    gop_cache: true,
    ping: 30,
    ping_timeout: 60
  },
  http: {
    port: 8000,
    allow_origin: '*'
  },
  trans: {
    ffmpeg: process.env.FFMPEG_PATH || '/usr/bin/ffmpeg',
    tasks: [
      {
        app: 'live',
        hls: true,
        hlsFlags: '[hls_time=2:hls_list_size=3:hls_flags=delete_segments]',
        hlsKeep: false // clean up old ts segments when stream terminates
      }
    ]
  }
};

let nms = null;

function startLiveServer() {
  if (process.env.DISABLE_RTMP === 'true') {
    logger.warn('RTMP Streaming Server is deactivated via DISABLE_RTMP configuration.');
    return;
  }

  const hlsDir = path.join(__dirname, '..', 'public', 'uploads');
  config.trans.tasks[0].hlsOutput = hlsDir;

  try {
    // Intercept address-in-use and access errors on RTMP/HLS ports to prevent server crashing
    const handlePortErrors = (err) => {
      if (err.code === 'EADDRINUSE' || err.code === 'EACCES') {
        logger.warn(`Port binding issue on RTMP/HLS server: ${err.message}. Deactivating live RTMP services safely. Uploads and HLS playback will continue working.`);
        stopLiveServer();
      } else {
        logger.error(`Live stream server exception: ${err.message}`);
      }
    };
    process.on('uncaughtException', handlePortErrors);

    nms = new NodeMediaServer(config);
    nms.run();
    logger.info('Live RTMP streaming media server listening on port 1935, HLS on port 8000.');

    // 1. Hook events to update database states when streaming starts or stops
    nms.on('postPublish', async (id, streamPath, args) => {
      logger.info(`Stream publication initiated: ${streamPath} (Session ID: ${id})`);
      // streamPath looks like "/live/stream_xyz"
      const streamId = streamPath.split('/').pop();
      const host = process.env.SERVER_HOST_URL || `https://stream-streamplay.up.railway.app`;
      const streamHlsUrl = `${host}/uploads/live/${streamId}/index.m3u8`;

      try {
        // Find if this stream corresponds to a known video or register a new one as dynamic Live
        const result = await db.query('SELECT * FROM videos WHERE id = $1', [streamId]);
        if (result.rows.length === 0) {
          // Dynamic Register of temporary live video in Database
          await db.query(`
            INSERT INTO videos (
              id, title, description, video_url, thumbnail_url, creator_id, creator_name,
              views, likes, dislikes, duration, upload_date, category, is_live, is_short, comments_count
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16)
          `, [
            streamId,
            'Live Stream from Creator',
            'Watch the live broadcast feed immediately.',
            streamHlsUrl,
            'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=640&q=80',
            'creator_dynamic',
            'Creator Studio Live',
            0, 0, 0, '00:00', 'Live Now', 'Gaming', true, false, 0
          ]);
        } else {
          // Update existing stream to Live status
          await db.query(`
            UPDATE videos 
            SET is_live = TRUE, video_url = $1 
            WHERE id = $2
          `, [streamHlsUrl, streamId]);
        }

        // Send Push notification
        sendPushNotification({
          topic: `all_subscribers`,
          title: `StreamPlay Live Alert!`,
          body: `A live broadcast has just started! Click to tune in.`,
          data: {
            videoId: streamId,
            type: 'live_broadcast',
          }
        }).catch(err => logger.error(`FCM live notification failed: ${err.message}`));

      } catch (err) {
        logger.error(`Failed to handle live stream publication database registration: ${err.message}`);
      }
    });

    nms.on('donePublish', async (id, streamPath, args) => {
      logger.info(`Stream publication ended: ${streamPath} (Session ID: ${id})`);
      const streamId = streamPath.split('/').pop();

      try {
        // Mark live stream as terminated
        await db.query(`
          UPDATE videos 
          SET is_live = FALSE 
          WHERE id = $1
        `, [streamId]);
      } catch (err) {
        logger.error(`Failed to handle live stream teardown database update: ${err.message}`);
      }
    });

  } catch (err) {
    logger.error(`Failed starting Node Media Server: ${err.message}`);
  }
}

function stopLiveServer() {
  if (nms) {
    nms.stop();
  }
}

module.exports = {
  startLiveServer,
  stopLiveServer
};
