const { S3Client, PutObjectCommand, DeleteObjectCommand, ListObjectsV2Command } = require('@aws-sdk/client-s3');
const fs = require('fs');
const path = require('path');
const logger = require('./logger');

const accessKeyId = process.env.R2_ACCESS_KEY_ID || process.env.AWS_ACCESS_KEY_ID;
const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY || process.env.AWS_SECRET_ACCESS_KEY;
const accountId = process.env.R2_ACCOUNT_ID;
const bucketName = process.env.R2_BUCKET || process.env.S3_BUCKET_NAME;

let endpoint = process.env.R2_ENDPOINT;
if (!endpoint && accountId) {
  endpoint = `https://${accountId}.r2.cloudflarestorage.com`;
} else if (!endpoint) {
  endpoint = process.env.S3_ENDPOINT;
}

const s3Client = accessKeyId && secretAccessKey
  ? new S3Client({
      region: 'auto',
      endpoint: endpoint,
      credentials: {
        accessKeyId: accessKeyId,
        secretAccessKey: secretAccessKey,
      },
      // Cloudflare R2 works best with virtual-host style or forcePathStyle depending on endpoints
      forcePathStyle: process.env.S3_FORCE_PATH_STYLE === 'true',
    })
  : null;

const BUCKET_NAME = bucketName;

/**
 * Uploads a local file to S3/R2. If not configured, it acts as a no-op,
 * since the file already exists locally and can be served from the local static folder.
 * 
 * @param {string} localFilePath Absolute path of the local file
 * @param {string} s3Key Key (path) under which the file should be saved in S3/R2
 * @param {string} contentType MIME type of the file
 * @returns {Promise<string>} The public URL of the uploaded file
 */
async function uploadFile(localFilePath, s3Key, contentType) {
  if (!s3Client || !BUCKET_NAME) {
    logger.info(`Cloudflare R2/S3 not configured. Serving local file: /uploads/${s3Key}`);
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
      // NOTE: Do NOT use ACL: 'public-read' here, as Cloudflare R2 does not support Object ACLs and throws NotImplemented.
    }));

    logger.info(`Successfully uploaded to Cloudflare R2: ${s3Key}`);
    
    // Construct the public URL
    let baseS3Url = process.env.R2_PUBLIC_URL || process.env.S3_PUBLIC_URL;
    if (!baseS3Url) {
      if (accountId) {
        baseS3Url = `https://${BUCKET_NAME}.${accountId}.r2.cloudflarestorage.com`;
      } else {
        baseS3Url = `https://${BUCKET_NAME}.s3.amazonaws.com`;
      }
    }
    
    // Ensure baseS3Url does not end with a trailing slash
    if (baseS3Url.endsWith('/')) {
      baseS3Url = baseS3Url.slice(0, -1);
    }

    return `${baseS3Url}/${s3Key}`;
  } catch (error) {
    logger.error(`Cloudflare R2/S3 upload failed for key: ${s3Key}. Falling back to local URL. Error: ${error.message}`);
    return `/uploads/${s3Key}`;
  }
}

/**
 * Recursively uploads a local directory to S3/R2 (used for uploading HLS streaming folders containing multiple segments).
 * 
 * @param {string} localDir Local folder path containing transcoded segments
 * @param {string} s3FolderPrefix S3/R2 folder path prefix (e.g. "vid_1234")
 * @returns {Promise<void>}
 */
async function uploadHLSDirectory(localDir, s3FolderPrefix) {
  if (!s3Client || !BUCKET_NAME) {
    logger.debug('Cloudflare R2/S3 not configured. HLS segments remain local.');
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
    logger.error(`Failed uploading HLS directory ${s3FolderPrefix} to Cloudflare R2: ${error.message}`);
  }
}

/**
 * Delete a file or directory from S3/R2
 * @param {string} s3Key Key of the object to delete
 */
async function deleteFile(s3Key) {
  if (!s3Client || !BUCKET_NAME) return;
  try {
    await s3Client.send(new DeleteObjectCommand({
      Bucket: BUCKET_NAME,
      Key: s3Key,
    }));
    logger.info(`Successfully deleted Cloudflare R2 object: ${s3Key}`);
  } catch (error) {
    logger.error(`Cloudflare R2 delete failed for key: ${s3Key}: ${error.message}`);
  }
}

async function listFiles(prefix = '') {
  if (!s3Client || !BUCKET_NAME) return [];
  try {
    const command = new ListObjectsV2Command({
      Bucket: BUCKET_NAME,
      Prefix: prefix,
    });
    const response = await s3Client.send(command);
    
    let baseS3Url = process.env.R2_PUBLIC_URL || process.env.S3_PUBLIC_URL;
    if (!baseS3Url) {
      if (accountId) {
        baseS3Url = `https://${BUCKET_NAME}.${accountId}.r2.cloudflarestorage.com`;
      } else {
        baseS3Url = `https://${BUCKET_NAME}.s3.amazonaws.com`;
      }
    }
    if (baseS3Url.endsWith('/')) {
      baseS3Url = baseS3Url.slice(0, -1);
    }

    return (response.Contents || []).map(item => ({
      key: item.Key,
      size: item.Size,
      lastModified: item.LastModified,
      url: `${baseS3Url}/${item.Key}`
    }));
  } catch (error) {
    logger.error(`Cloudflare R2 list files failed: ${error.message}`);
    return [];
  }
}

module.exports = {
  uploadFile,
  uploadHLSDirectory,
  deleteFile,
  listFiles,
  isConfigured: () => !!s3Client
};
