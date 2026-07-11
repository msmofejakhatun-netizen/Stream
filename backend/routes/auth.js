const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const db = require('../config/db');
const { JWT_SECRET, authenticateToken } = require('../middleware/auth');

// Local fallback user database in case PostgreSQL is down (highly robust)
const localUsers = new Map();

// Helper to check user existence and query
async function findUserByEmail(email) {
  try {
    const { rows } = await db.query('SELECT * FROM users WHERE email = $1', [email]);
    if (rows.length > 0) {
      return {
        id: rows[0].id,
        email: rows[0].email,
        passwordHash: rows[0].password_hash,
        displayName: rows[0].display_name,
        avatarUrl: rows[0].avatar_url,
        isGuest: rows[0].is_guest,
        joinedDate: rows[0].joined_date,
        subscribersCount: rows[0].subscribers_count,
        role: rows[0].role || 'User',
        isBanned: rows[0].is_banned || false,
        isVerified: rows[0].is_verified || false,
        isPremium: rows[0].is_premium || false
      };
    }
  } catch (err) {
    console.error('Database query error on findUserByEmail:', err.message);
  }
  return localUsers.get(email) || null;
}

async function createUser({ id, email, passwordHash, displayName, avatarUrl, isGuest, role }) {
  const user = {
    id,
    email,
    passwordHash,
    displayName,
    avatarUrl: avatarUrl || `https://images.unsplash.com/photo-${Math.floor(Math.random() * 5000) + 1500000000000}?auto=format&fit=crop&w=120&q=80`,
    isGuest: !!isGuest,
    joinedDate: 'Jul 2026',
    subscribersCount: 0,
    role: role || 'User',
    isBanned: false,
    isVerified: false,
    isPremium: false
  };

  try {
    await db.query(`
      INSERT INTO users (id, email, password_hash, display_name, avatar_url, is_guest, joined_date, subscribers_count, role)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
    `, [user.id, user.email, user.passwordHash, user.displayName, user.avatarUrl, user.isGuest, user.joinedDate, user.subscribersCount, user.role]);
  } catch (err) {
    console.error('Database error on createUser, storing in memory fallback:', err.message);
    localUsers.set(email, user);
  }
  return user;
}

// 1. Email Registration
router.post('/register', async (req, res) => {
  const { email, password, displayName } = req.body;
  if (!email || !password || !displayName) {
    return res.status(400).json({ error: 'Please provide email, password, and displayName' });
  }

  try {
    const existing = await findUserByEmail(email);
    if (existing) {
      return res.status(400).json({ error: 'User already exists with this email' });
    }

    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);
    const userId = 'u_' + uuidv4().substring(0, 8);

    const user = await createUser({
      id: userId,
      email,
      passwordHash,
      displayName
    });

    const token = jwt.sign({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      role: user.role || 'User',
      isVerified: user.isVerified || false,
      isPremium: user.isPremium || false
    }, JWT_SECRET, { expiresIn: '7d' });

    res.status(201).json({
      token,
      user: {
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        isGuest: user.isGuest,
        joinedDate: user.joinedDate,
        subscribersCount: user.subscribersCount,
        role: user.role || 'User',
        isVerified: user.isVerified || false,
        isPremium: user.isPremium || false
      }
    });
  } catch (err) {
    res.status(500).json({ error: 'Registration failed: ' + err.message });
  }
});

// 2. Email Login
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'Please provide email and password' });
  }

  try {
    const user = await findUserByEmail(email);
    if (!user) {
      return res.status(400).json({ error: 'Invalid credentials' });
    }

    if (user.isBanned) {
      return res.status(403).json({ error: 'Your account has been banned by an administrator.' });
    }

    const isMatch = await bcrypt.compare(password, user.passwordHash);
    if (!isMatch) {
      return res.status(400).json({ error: 'Invalid credentials' });
    }

    const token = jwt.sign({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      role: user.role || 'User',
      isVerified: user.isVerified || false,
      isPremium: user.isPremium || false
    }, JWT_SECRET, { expiresIn: '7d' });

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        isGuest: user.isGuest,
        joinedDate: user.joinedDate,
        subscribersCount: user.subscribersCount,
        role: user.role || 'User',
        isVerified: user.isVerified || false,
        isPremium: user.isPremium || false
      }
    });
  } catch (err) {
    res.status(500).json({ error: 'Login failed: ' + err.message });
  }
});

// 3. Google Federated Sign-In
router.post('/google', async (req, res) => {
  const { email, displayName, avatarUrl } = req.body;
  if (!email) {
    return res.status(400).json({ error: 'Google sign-in requires email' });
  }

  try {
    let user = await findUserByEmail(email);
    if (!user) {
      // Create new federated google user
      const dummyPassword = await bcrypt.hash(uuidv4(), 10);
      user = await createUser({
        id: 'g_' + uuidv4().substring(0, 8),
        email,
        passwordHash: dummyPassword,
        displayName: displayName || email.split('@')[0],
        avatarUrl: avatarUrl
      });
    }

    if (user.isBanned) {
      return res.status(403).json({ error: 'Your account has been banned by an administrator.' });
    }

    const token = jwt.sign({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      role: user.role || 'User',
      isVerified: user.isVerified || false,
      isPremium: user.isPremium || false
    }, JWT_SECRET, { expiresIn: '7d' });

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        isGuest: user.isGuest,
        joinedDate: user.joinedDate,
        subscribersCount: user.subscribersCount,
        role: user.role || 'User',
        isVerified: user.isVerified || false,
        isPremium: user.isPremium || false
      }
    });
  } catch (err) {
    res.status(500).json({ error: 'Google sign-in failed: ' + err.message });
  }
});

// 4. Guest Session Initiation
router.post('/guest', async (req, res) => {
  try {
    const guestId = 'guest_' + uuidv4().substring(0, 6);
    const guestEmail = `${guestId}@streamplay.com`;
    const dummyPassword = await bcrypt.hash(uuidv4(), 10);

    const user = await createUser({
      id: guestId,
      email: guestEmail,
      passwordHash: dummyPassword,
      displayName: 'Guest Viewer',
      avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120&q=80',
      isGuest: true
    });

    const token = jwt.sign({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      isGuest: true,
      role: 'User',
      isVerified: false,
      isPremium: false
    }, JWT_SECRET, { expiresIn: '1d' });

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        isGuest: true,
        joinedDate: user.joinedDate,
        subscribersCount: 0,
        role: 'User',
        isVerified: false,
        isPremium: false
      }
    });
  } catch (err) {
    res.status(500).json({ error: 'Guest session initialization failed: ' + err.message });
  }
});

// 5. Forgot Password Mock Process
router.post('/forgot-password', (req, res) => {
  const { email } = req.body;
  if (!email) {
    return res.status(400).json({ error: 'Email is required' });
  }
  // In real life, send grid or ses, here we log it and send confirmation
  console.log(`Password reset link requested for: ${email}`);
  res.json({ message: 'If a user with this email exists, a password reset link has been dispatched successfully.' });
});

// 6. Get Current Profile
router.get('/profile', authenticateToken, async (req, res) => {
  try {
    const user = await findUserByEmail(req.user.email);
    if (!user) {
      return res.status(404).json({ error: 'Profile not found' });
    }
    res.json({
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      avatarUrl: user.avatarUrl,
      isGuest: user.isGuest,
      joinedDate: user.joinedDate,
      subscribersCount: user.subscribersCount
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch profile: ' + err.message });
  }
});

// 7. Update Profile
router.put('/profile', authenticateToken, async (req, res) => {
  const { displayName, avatarUrl } = req.body;
  try {
    const email = req.user.email;
    const user = await findUserByEmail(email);
    if (!user) {
      return res.status(404).json({ error: 'Profile not found' });
    }

    const updatedDisplayName = displayName || user.displayName;
    const updatedAvatarUrl = avatarUrl || user.avatarUrl;

    try {
      await db.query('UPDATE users SET display_name = $1, avatar_url = $2 WHERE email = $3', [updatedDisplayName, updatedAvatarUrl, email]);
    } catch (err) {
      console.error('Postgres update profile error:', err.message);
    }

    // Always keep local in sync
    const updatedUser = {
      ...user,
      displayName: updatedDisplayName,
      avatarUrl: updatedAvatarUrl
    };
    localUsers.set(email, updatedUser);

    res.json({
      id: updatedUser.id,
      email: updatedUser.email,
      displayName: updatedUser.displayName,
      avatarUrl: updatedUser.avatarUrl,
      isGuest: updatedUser.isGuest,
      joinedDate: updatedUser.joinedDate,
      subscribersCount: updatedUser.subscribersCount
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to update profile: ' + err.message });
  }
});

module.exports = router;
