const db = require('../config/db');
const { processVideo } = require('./transcoder');
const { uploadHLSDirectory, uploadFile } = require('./s3');
const { sendPushNotification } = require('./firebase');
const logger = require('./logger');
const path = require('path');
const fs = require('fs');

let workerRunning = false;
let pollingInterval = null;

/**
 * Initializes the transcoding jobs table in PostgreSQL
 */
async function initQueueTable() {
  try {
    await db.query(`
      CREATE TABLE IF NOT EXISTS transcoding_jobs (
        id VARCHAR(100) PRIMARY KEY,
        video_id VARCHAR(100) NOT NULL,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        category VARCHAR(100) NOT NULL,
        is_short BOOLEAN DEFAULT FALSE,
        input_path TEXT NOT NULL,
        status VARCHAR(20) DEFAULT 'pending', -- pending, processing, completed, failed
        error_message TEXT,
        creator_id VARCHAR(100) NOT NULL,
        creator_name VARCHAR(100) NOT NULL,
        creator_avatar TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);
    logger.info('Transcoding job queue table verified/created.');
  } catch (error) {
    logger.error(`Failed to initialize transcoding job queue table: ${error.message}`);
  }
}

/**
 * Adds a new transcoding job to the queue
 */
async function addJob({ videoId, title, description, category, isShort, inputPath, creatorId, creatorName, creatorAvatar }) {
  const jobId = 'job_' + Math.random().toString(36).substring(2, 10);
  try {
    await db.query(`
      INSERT INTO transcoding_jobs (
        id, video_id, title, description, category, is_short, input_path, creator_id, creator_name, creator_avatar
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `, [jobId, videoId, title, description || '', category, isShort, inputPath, creatorId, creatorName, creatorAvatar]);
    
    logger.info(`Job added to transcoding queue: ${jobId} for video ${videoId}`);
    // Trigger worker processing asynchronously
    triggerWorker();
    return jobId;
  } catch (error) {
    logger.error(`Failed to add job to transcoding queue: ${error.message}`);
    throw error;
  }
}

/**
 * Single-threaded background worker processing jobs one by one to avoid CPU starvation
 */
async function processNextJob() {
  if (workerRunning) return;
  workerRunning = true;

  try {
    // 1. Claim next pending job atomically using FOR UPDATE SKIP LOCKED
    const claimResult = await db.query(`
      UPDATE transcoding_jobs
      SET status = 'processing', updated_at = CURRENT_TIMESTAMP
      WHERE id = (
        SELECT id FROM transcoding_jobs 
        WHERE status = 'pending' 
        ORDER BY created_at ASC 
        LIMIT 1 
        FOR UPDATE SKIP LOCKED
      )
      RETURNING *;
    `);

    if (claimResult.rows.length === 0) {
      workerRunning = false;
      return; // No pending jobs
    }

    const job = claimResult.rows[0];
    logger.info(`Claimed and starting transcoding job: ${job.id} for Video: ${job.video_id}`);

    // 2. Perform transcoding and thumbnail generation
    try {
      if (!fs.existsSync(job.input_path)) {
        throw new Error(`Original input file not found: ${job.input_path}`);
      }

      // Transcode HLS locally
      const results = await processVideo(job.input_path, job.video_id);

      // S3 Cloud storage sync if enabled
      const host = process.env.SERVER_HOST_URL || `http://localhost:5000`;
      let finalVideoUrl = results.hlsUrl.startsWith('http') ? results.hlsUrl : `${host}${results.hlsUrl}`;
      let finalThumbnailUrl = results.thumbnailUrl.startsWith('http') ? results.thumbnailUrl : `${host}${results.thumbnailUrl}`;

      if (results.hlsUrl.startsWith('/uploads')) {
        const localVideoFolder = path.join(__dirname, '..', 'public', 'uploads', job.video_id);
        const s3Prefix = `${job.video_id}`;

        // Sync local playlist and ts files to cloud
        await uploadHLSDirectory(localVideoFolder, s3Prefix);

        // Map URL back to S3
        if (process.env.S3_BUCKET_NAME) {
          const defaultEndpoint = `https://${process.env.S3_BUCKET_NAME}.s3.${process.env.AWS_REGION || 'us-east-1'}.amazonaws.com`;
          const baseS3Url = process.env.S3_PUBLIC_URL || defaultEndpoint;
          finalVideoUrl = `${baseS3Url}/${s3Prefix}/master.m3u8`;
          finalThumbnailUrl = `${baseS3Url}/${s3Prefix}/thumbnail.jpg`;
        }
      }

      // 3. Save processed video into the main videos database
      await db.query(`
        INSERT INTO videos (
          id, title, description, video_url, thumbnail_url, creator_id,
          creator_name, creator_avatar, views, likes, dislikes,
          duration, upload_date, category, is_live, is_short, comments_count
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17)
        ON CONFLICT (id) DO UPDATE SET 
          video_url = EXCLUDED.video_url,
          thumbnail_url = EXCLUDED.thumbnail_url,
          duration = EXCLUDED.duration;
      `, [
        job.video_id, job.title, job.description, finalVideoUrl, finalThumbnailUrl, job.creator_id,
        job.creator_name, job.creator_avatar || 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80',
        0, 0, 0,
        results.duration, 'Just now', job.category, false, job.is_short, 0
      ]);

      // 4. Update job status to completed
      await db.query(`
        UPDATE transcoding_jobs 
        SET status = 'completed', updated_at = CURRENT_TIMESTAMP 
        WHERE id = $1
      `, [job.id]);

      logger.info(`Successfully completed transcoding job: ${job.id}`);

      // 5. Cleanup raw uploaded video file to conserve disk space
      try {
        fs.unlinkSync(job.input_path);
        logger.info(`Cleaned up raw temporary file: ${job.input_path}`);
      } catch (unlinkErr) {
        logger.warn(`Failed to delete raw temp video: ${unlinkErr.message}`);
      }

      // 6. Dispatch Firebase push notifications to subscribers about new upload
      sendPushNotification({
        topic: `creator_${job.creator_id}`,
        title: `${job.creator_name} uploaded a new video!`,
        body: job.title,
        data: {
          videoId: job.video_id,
          type: 'new_upload',
        }
      }).catch(err => logger.error(`FCM upload notification failed: ${err.message}`));

    } catch (jobError) {
      logger.error(`Job processing failed for ${job.id}: ${jobError.message}`);
      await db.query(`
        UPDATE transcoding_jobs 
        SET status = 'failed', error_message = $1, updated_at = CURRENT_TIMESTAMP 
        WHERE id = $2
      `, [jobError.message, job.id]);
    }

  } catch (dbError) {
    logger.error(`Transcoding queue worker database error: ${dbError.message}`);
  } finally {
    workerRunning = false;
    // Schedule next processing immediately in case there are more pending jobs
    setTimeout(processNextJob, 500);
  }
}

function triggerWorker() {
  processNextJob().catch(err => logger.error(`Worker error: ${err.message}`));
}

/**
 * Starts periodic polling for transcoding jobs (failsafe)
 */
function startQueueWorker() {
  initQueueTable().then(() => {
    triggerWorker();
    // 30 seconds poll fallback interval
    pollingInterval = setInterval(triggerWorker, 30000);
    logger.info('Transcoding Queue worker polling started.');
  });
}

function stopQueueWorker() {
  if (pollingInterval) {
    clearInterval(pollingInterval);
  }
}

module.exports = {
  addJob,
  startQueueWorker,
  stopQueueWorker,
  triggerWorker
};
