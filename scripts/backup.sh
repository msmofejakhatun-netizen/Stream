#!/bin/bash

# Configuration
DB_NAME=${POSTGRES_DB:-"streamplay"}
DB_USER=${POSTGRES_USER:-"stream"}
DB_PASS=${POSTGRES_PASSWORD:-"play"}
DB_HOST=${POSTGRES_HOST:-"localhost"}
DB_PORT=${POSTGRES_PORT:-"5432"}

BACKUP_DIR="/app/backups"
DATE=$(date +%Y-%m-%d_%H-%M-%S)
BACKUP_FILENAME="${DB_NAME}_backup_${DATE}.sql.gz"
BACKUP_FILEPATH="${BACKUP_DIR}/${BACKUP_FILENAME}"

echo "===================================================="
echo "Starting Database Backup for ${DB_NAME}..."
echo "Timestamp: $(date)"
echo "===================================================="

# Ensure backup directory exists
mkdir -p "$BACKUP_DIR"

# Perform pg_dump and compress on-the-fly
PGPASSWORD="$DB_PASS" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILEPATH"

if [ $? -eq 0 ]; then
  echo "✅ Database backup created successfully at: ${BACKUP_FILEPATH}"
  
  # Upload to S3 cloud storage if S3 configured
  if [ -n "$AWS_ACCESS_KEY_ID" ] && [ -n "$AWS_SECRET_ACCESS_KEY" ] && [ -n "$S3_BUCKET_NAME" ]; then
    echo "Uploading backup to AWS S3..."
    if command -v aws &> /dev/null; then
      aws s3 cp "$BACKUP_FILEPATH" "s3://${S3_BUCKET_NAME}/backups/${BACKUP_FILENAME}"
      if [ $? -eq 0 ]; then
        echo "✅ Uploaded backup to cloud storage s3://${S3_BUCKET_NAME}/backups/${BACKUP_FILENAME}"
      else
        echo "❌ S3 upload failed using AWS CLI."
      fi
    else
      echo "⚠️ AWS CLI not installed. Skipping automatic Cloud S3 upload."
    fi
  fi
  
  # Prune local backups older than 30 days
  echo "Pruning backups older than 30 days..."
  find "$BACKUP_DIR" -type f -name "${DB_NAME}_backup_*.sql.gz" -mtime +30 -exec rm {} \;
  echo "✅ Pruning complete."
else
  echo "❌ Error: Database backup failed."
  exit 1
fi

echo "===================================================="
echo "Backup execution finished successfully."
echo "===================================================="
