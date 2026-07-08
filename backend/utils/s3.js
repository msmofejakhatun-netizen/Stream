const { S3Client, PutObjectCommand, DeleteObjectCommand } = require('@aws-sdk/client-s3');
const fs = require('fs');
const path = require('path');
const logger = require('./logger');

const s3Client = process.env.AWS_ACCESS_KEY_ID && process.env.AWS_SECRET_ACCESS_KEY
  ? new S3Client({
      region: process.env.AWS_REGION || 'us-east-1',
      endpoint: process.env.S3_ENDPOINT || undefined, // Custom endpoint for MinIO/GCS compatibility
      credentials: {
        accessKeyId: process.env.AWS_ACCESS_KEY_ID,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
      },
      forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
    })
  : null;

const BUCKET_NAME = process.env.S3_BUCKET_NAME;

/**
 * Uploads a local file to S3. If S3 client is not configured, it acts as a no-op,
 * since the file already exists locally and can be served from the local static folder.
 * 
 * @param {string} localFilePath Absolute path of the local file
 * @param {string} s3Key Key (path) under which the file should be saved in S3
 * @param {string} contentType MIME type of the file
 * @returns {Promise<string>} The public URL of the uploaded file
 */
async function uploadFile(localFilePath, s3Key, contentType) {
  if (!s3Client || !BUCKET_NAME) {
    logger.info(`S3 not configured. Serving local file: /uploads/${s3Key}`);
    // Return relative URL for static Express serving fallback
    return `/uploads/${s3Key}`;
  }

  try {
    const fileStream = fs.createReadStream(localFilePath);
    await s3Client.send(new PutObjectCommand({
      Bucket: BUCKET_NAME,
      Key: s3Key,
      Body: fileStream,
      ContentType: contentType,
      ACL: 'public-read',
    }));

    logger.info(`Successfully uploaded to S3: ${s3Key}`);
    const defaultEndpoint = `https://${BUCKET_NAME}.s3.${process.env.AWS_REGION || 'us-east-1'}.amazonaws.com`;
    const baseS3Url = process.env.S3_PUBLIC_URL || defaultEndpoint;
    return `${baseS3Url}/${s3Key}`;
  } catch (error) {
    logger.error(`S3 upload failed for key: ${s3Key}. Falling back to local URL. Error: ${error.message}`);
    return `/uploads/${s3Key}`;
  }
}

/**
 * Recursively uploads a local directory to S3 (used for uploading HLS streaming folders containing multiple segments).
 * 
 * @param {string} localDir Local folder path containing transcoded segments
 * @param {string} s3FolderPrefix S3 folder path prefix (e.g. "vid_1234")
 * @returns {Promise<void>}
 */
async function uploadHLSDirectory(localDir, s3FolderPrefix) {
  if (!s3Client || !BUCKET_NAME) {
    logger.debug('S3 not configured. HLS segments remain local.');
    return;
  }

  try {
    const files = fs.readdirSync(localDir);
    for (const file of files) {
      const fullPath = path.join(localDir, file);
      if (fs.statSync(fullPath).isFile()) {
        const s3Key = `${s3FolderPrefix}/${file}`;
        let contentType = 'application/octet-stream';
        if (file.endsWith('.m3u8')) contentType = 'application/x-mpegURL';
        else if (file.endsWith('.ts')) contentType = 'video/MP2T';
        else if (file.endsWith('.jpg') || file.endsWith('.jpeg')) contentType = 'image/jpeg';

        await uploadFile(fullPath, s3Key, contentType);
      }
    }
  } catch (error) {
    logger.error(`Failed uploading HLS directory ${s3FolderPrefix} to S3: ${error.message}`);
  }
}

/**
 * Delete a file or directory from S3
 * @param {string} s3Key Key of the object to delete
 */
async function deleteFile(s3Key) {
  if (!s3Client || !BUCKET_NAME) return;
  try {
    await s3Client.send(new DeleteObjectCommand({
      Bucket: BUCKET_NAME,
      Key: s3Key,
    }));
    logger.info(`Successfully deleted S3 object: ${s3Key}`);
  } catch (error) {
    logger.error(`S3 delete failed for key: ${s3Key}: ${error.message}`);
  }
}

module.exports = {
  uploadFile,
  uploadHLSDirectory,
  deleteFile,
  isConfigured: () => !!s3Client
};
