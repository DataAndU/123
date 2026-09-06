import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { z } from 'zod';
import { pool } from '../db/pool';
import { signToken } from '../auth/jwt';
import { asyncHandler, HttpError } from '../util/asyncHandler';
import { createPlayerState, getFullPlayerView } from '../player/repository';

export const authRouter = Router();

const registerSchema = z.object({
  username: z.string().min(3).max(24).regex(/^[a-zA-Z0-9_]+$/, 'letters, numbers, underscore only'),
  email: z.string().email(),
  password: z.string().min(8).max(72),
  displayName: z.string().min(1).max(40)
});

authRouter.post(
  '/register',
  asyncHandler(async (req, res) => {
    const parsed = registerSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, parsed.error.issues.map((i) => i.message).join('; '));
    const { username, email, password, displayName } = parsed.data;

    const existing = await pool.query('SELECT 1 FROM users WHERE username = $1 OR email = $2', [username, email]);
    if (existing.rows.length > 0) throw new HttpError(409, 'Username or email already taken');

    const passwordHash = await bcrypt.hash(password, 10);
    const userId = uuidv4();
    await pool.query(
      'INSERT INTO users (id, username, email, password_hash, display_name) VALUES ($1, $2, $3, $4, $5)',
      [userId, username, email, passwordHash, displayName]
    );
    await createPlayerState(userId);

    const token = signToken({ userId, username });
    const player = await getFullPlayerView(userId);
    res.status(201).json({ token, player });
  })
);

const loginSchema = z.object({
  usernameOrEmail: z.string().min(1),
  password: z.string().min(1)
});

authRouter.post(
  '/login',
  asyncHandler(async (req, res) => {
    const parsed = loginSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Missing credentials');
    const { usernameOrEmail, password } = parsed.data;

    const result = await pool.query('SELECT id, username, password_hash FROM users WHERE username = $1 OR email = $1', [usernameOrEmail]);
    if (result.rows.length === 0) throw new HttpError(401, 'Invalid credentials');
    const user = result.rows[0];
    const valid = await bcrypt.compare(password, user.password_hash);
    if (!valid) throw new HttpError(401, 'Invalid credentials');

    const token = signToken({ userId: user.id, username: user.username });
    const player = await getFullPlayerView(user.id);
    res.json({ token, player });
  })
);
