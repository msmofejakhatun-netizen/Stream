const express = require('express');
const router = express.Router();
const { authenticateToken } = require('../middleware/auth');
const { subscribeToTopic } = require('../utils/firebase');
const db = require('../config/db');
const logger = require('../utils/logger');

/**
 * 1. Register a device's FCM token
 */
router.post('/register', authenticateToken, async (req, res) => {
  const { fcmToken } = req.body;
  const userId = req.user.id;

  if (!fcmToken) {
    return res.status(400).json({ error: 'FCM token is required.' });
  }

  try {
    // Save token to user profile or devices table
    await db.query('ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(255);').catch(() => {});
    await db.query(`
      UPDATE users 
      SET fcm_token = $1 
      WHERE id = $2
    `, [fcmToken, userId]);

    // Also auto-subscribe user to general broadcasts
    await subscribeToTopic(fcmToken, 'all_subscribers');

    res.json({ message: 'Device token registered successfully.' });
  } catch (error) {
    logger.error(`Failed to register FCM token for user ${userId}: ${error.message}`);
    res.status(500).json({ error: 'Failed to save registration token.' });
  }
});

/**
 * 2. Subscribe to creator channel notifications
 */
router.post('/subscribe-channel', authenticateToken, async (req, res) => {
  const { creatorId } = req.body;
  const userId = req.user.id;

  if (!creatorId) {
    return res.status(400).json({ error: 'Creator ID is required.' });
  }

  try {
    const userResult = await db.query('SELECT fcm_token FROM users WHERE id = $1', [userId]);
    const fcmToken = userResult.rows[0]?.fcm_token;

    if (fcmToken) {
      await subscribeToTopic(fcmToken, `creator_${creatorId}`);
    }

    res.json({ message: `Subscribed to creator_${creatorId} alerts.` });
  } catch (error) {
    logger.error(`Failed to subscribe user ${userId} to creator ${creatorId} topic: ${error.message}`);
    res.status(500).json({ error: 'Failed subscribing to channel notifications.' });
  }
});

module.exports = router;
