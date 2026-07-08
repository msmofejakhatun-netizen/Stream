const client = require('prom-client');
const logger = require('../utils/logger');

// Create a Registry to register metrics
const register = new client.Registry();

// Add default metrics (CPU, Memory, garbage collection, etc.)
client.collectDefaultMetrics({
  register,
  prefix: 'streamplay_api_',
});

// Create custom metrics for API monitoring
const httpRequestCounter = new client.Counter({
  name: 'streamplay_http_requests_total',
  help: 'Total number of HTTP requests processed',
  labelNames: ['method', 'route', 'status_code'],
});

const httpRequestDuration = new client.Histogram({
  name: 'streamplay_http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.1, 0.3, 0.5, 1, 1.5, 2, 5],
});

const videoTranscodeCounter = new client.Counter({
  name: 'streamplay_video_transcodes_total',
  help: 'Total number of video transcoding jobs',
  labelNames: ['status'], // completed, failed
});

// Register custom metrics
register.registerMetric(httpRequestCounter);
register.registerMetric(httpRequestDuration);
register.registerMetric(videoTranscodeCounter);

/**
 * Middleware to trace and log metrics for every incoming request
 */
function metricsMiddleware(req, res, next) {
  // Exclude /metrics endpoint itself to avoid noise
  if (req.originalUrl === '/metrics' || req.originalUrl === '/favicon.ico') {
    return next();
  }

  const start = process.hrtime();

  res.on('finish', () => {
    const duration = process.hrtime(start);
    const durationInSeconds = duration[0] + duration[1] / 1e9;
    const route = req.route ? req.route.path : req.originalUrl;
    const statusCode = res.statusCode;

    httpRequestCounter.labels(req.method, route, statusCode).inc();
    httpRequestDuration.labels(req.method, route, statusCode).observe(durationInSeconds);
  });

  next();
}

/**
 * Express handler for exposing collected metrics
 */
async function exposeMetrics(req, res) {
  try {
    res.set('Content-Type', register.contentType);
    const metricsResult = await register.metrics();
    res.end(metricsResult);
  } catch (error) {
    logger.error(`Prometheus metrics serialization failed: ${error.message}`);
    res.status(500).end(error.message);
  }
}

module.exports = {
  metricsMiddleware,
  exposeMetrics,
  videoTranscodeCounter
};
