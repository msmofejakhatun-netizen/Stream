# Stage 1: Build & Dependencies
FROM node:18-slim AS builder

WORKDIR /usr/src/app

# Install build dependencies for native modules
RUN apt-get update && apt-get install -y \
    python3 \
    make \
    g++ \
    gcc \
    && rm -rf /var/lib/apt/lists/*

COPY package*.json ./

# Install all dependencies (including devDependencies)
RUN npm ci

COPY . .

# Stage 2: Production Release
FROM node:18-slim AS runner

WORKDIR /app

# Install FFmpeg and clean cache to keep image small
RUN apt-get update && apt-get install -y \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY package*.json ./

# Install only production dependencies
RUN npm ci --only=production

# Copy built application source files from builder stage
COPY --from=builder /usr/src/app .

# Create directory structure for uploads, live, and logs
RUN mkdir -p public/uploads/temp public/uploads/live logs && \
    chown -R node:node /app

# Expose ports: 5000 (Express API), 1935 (RTMP streaming), 8000 (Media Server HLS)
EXPOSE 5000 1935 8000

ENV NODE_ENV=production
ENV PORT=5000

# Run container as non-privileged node user for security
USER node

CMD ["node", "index.js"]
