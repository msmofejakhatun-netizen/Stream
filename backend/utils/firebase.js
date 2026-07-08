const admin = require('firebase-admin');
const logger = require('./logger');

let messaging = null;

try {
  const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

  if (serviceAccountJson) {
    const credentials = JSON.parse(serviceAccountJson);
    admin.initializeApp({
      credential: admin.credential.cert(credentials),
    });
    messaging = admin.messaging();
    logger.info('Firebase Admin initialized via environment JSON configuration.');
  } else if (serviceAccountPath) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccountPath),
    });
    messaging = admin.messaging();
    logger.info(`Firebase Admin initialized via service account file path: ${serviceAccountPath}`);
  } else {
    logger.warn('Firebase push notifications are inactive: FIREBASE_SERVICE_ACCOUNT_PATH or FIREBASE_SERVICE_ACCOUNT_JSON is not configured.');
  }
} catch (error) {
  logger.error(`Failed to initialize Firebase Admin SDK: ${error.message}`);
}

/**
 * Sends a push notification to a specific token or a broadcast topic
 * 
 * @param {object} payload Notification payload
 * @param {string} payload.title Title of the push notification
 * @param {string} payload.body Body text
 * @param {string} [payload.token] Target FCM registration token
 * @param {string} [payload.topic] Target topic (e.g. "new_uploads", "creator_123")
 * @param {object} [payload.data] Custom data key-value payload
 * @returns {Promise<boolean>} Success status
 */
async function sendPushNotification({ title, body, token, topic, data = {} }) {
  if (!messaging) {
    logger.warn(`Push notification skipped (Firebase inactive). Title: "${title}", Body: "${body}"`);
    return false;
  }

  const message = {
    notification: {
      title,
      body,
    },
    data: {
      ...data,
      click_action: 'FLUTTER_NOTIFICATION_CLICK', // standard fallback for app navigation routing
    },
  };

  if (token) {
    message.token = token;
  } else if (topic) {
    message.topic = topic;
  } else {
    logger.error('Cannot send notification: neither token nor topic was provided.');
    return false;
  }

  try {
    const response = await messaging.send(message);
    logger.info(`Firebase push notification dispatched successfully: ${response}`);
    return true;
  } catch (error) {
    logger.error(`Failed to send Firebase push notification: ${error.message}`);
    return false;
  }
}

/**
 * Subscribes a registration token to a specific notification topic (e.g. channel ID for subscriptions)
 * 
 * @param {string} token FCM registration token
 * @param {string} topic Topic name to subscribe to
 * @returns {Promise<boolean>}
 */
async function subscribeToTopic(token, topic) {
  if (!messaging) return false;
  try {
    await messaging.subscribeToTopic(token, topic);
    logger.info(`Subscribed token ${token.substring(0, 10)}... to topic: ${topic}`);
    return true;
  } catch (error) {
    logger.error(`Topic subscription failed: ${error.message}`);
    return false;
  }
}

module.exports = {
  sendPushNotification,
  subscribeToTopic,
  isConfigured: () => !!messaging
};
