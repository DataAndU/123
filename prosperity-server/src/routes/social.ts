import { Router } from 'express';
import { v4 as uuidv4 } from 'uuid';
import { z } from 'zod';
import { pool } from '../db/pool';
import { requireAuth } from '../middleware/auth';
import { asyncHandler, HttpError } from '../util/asyncHandler';

export const socialRouter = Router();
socialRouter.use(requireAuth);

socialRouter.get(
  '/users/search',
  asyncHandler(async (req, res) => {
    const q = String(req.query.q || '').trim();
    if (q.length < 2) return res.json([]);
    const result = await pool.query(
      `SELECT id, username, display_name FROM users WHERE username ILIKE $1 AND id != $2 LIMIT 20`,
      [`%${q}%`, req.userId]
    );
    res.json(result.rows.map((r) => ({ userId: r.id, username: r.username, displayName: r.display_name })));
  })
);

const requestSchema = z.object({ targetUsername: z.string().min(1) });

socialRouter.post(
  '/friends/request',
  asyncHandler(async (req, res) => {
    const parsed = requestSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'targetUsername required');
    const target = await pool.query('SELECT id FROM users WHERE username = $1', [parsed.data.targetUsername]);
    if (target.rows.length === 0) throw new HttpError(404, 'User not found');
    const targetId = target.rows[0].id;
    if (targetId === req.userId) throw new HttpError(400, "You can't friend yourself");

    const existing = await pool.query(
      `SELECT id, status FROM friendships WHERE (requester_id = $1 AND addressee_id = $2) OR (requester_id = $2 AND addressee_id = $1)`,
      [req.userId, targetId]
    );
    if (existing.rows.length > 0) throw new HttpError(409, `Friendship already ${existing.rows[0].status}`);

    await pool.query(
      'INSERT INTO friendships (id, requester_id, addressee_id, status) VALUES ($1, $2, $3, $4)',
      [uuidv4(), req.userId, targetId, 'pending']
    );
    res.status(201).json({ ok: true });
  })
);

const respondSchema = z.object({ friendshipId: z.string().uuid(), accept: z.boolean() });

socialRouter.post(
  '/friends/respond',
  asyncHandler(async (req, res) => {
    const parsed = respondSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Invalid request');
    const { friendshipId, accept } = parsed.data;

    const result = await pool.query('SELECT * FROM friendships WHERE id = $1 AND addressee_id = $2 AND status = $3', [
      friendshipId,
      req.userId,
      'pending'
    ]);
    if (result.rows.length === 0) throw new HttpError(404, 'Pending request not found');

    await pool.query('UPDATE friendships SET status = $1, responded_at = now() WHERE id = $2', [
      accept ? 'accepted' : 'declined',
      friendshipId
    ]);
    res.json({ ok: true });
  })
);

socialRouter.get(
  '/friends',
  asyncHandler(async (req, res) => {
    const result = await pool.query(
      `SELECT f.id, f.status, f.requester_id, f.addressee_id,
              u.username, u.display_name
       FROM friendships f
       JOIN users u ON u.id = (CASE WHEN f.requester_id = $1 THEN f.addressee_id ELSE f.requester_id END)
       WHERE f.requester_id = $1 OR f.addressee_id = $1
       ORDER BY f.created_at DESC`,
      [req.userId]
    );
    res.json(
      result.rows.map((r) => ({
        friendshipId: r.id,
        status: r.status,
        direction: r.requester_id === req.userId ? 'outgoing' : 'incoming',
        username: r.username,
        displayName: r.display_name
      }))
    );
  })
);

socialRouter.get(
  '/chat/:channel/history',
  asyncHandler(async (req, res) => {
    const channel = req.params.channel;
    const result = await pool.query(
      'SELECT id, sender_id, sender_username, body, sent_at FROM chat_messages WHERE channel = $1 ORDER BY sent_at DESC LIMIT 50',
      [channel]
    );
    res.json(
      result.rows
        .reverse()
        .map((r) => ({ id: r.id, senderId: r.sender_id, senderUsername: r.sender_username, body: r.body, sentAt: r.sent_at }))
    );
  })
);
