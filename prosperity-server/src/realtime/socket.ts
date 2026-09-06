import { Server, Socket } from 'socket.io';
import { v4 as uuidv4 } from 'uuid';
import { verifyToken } from '../auth/jwt';
import { pool } from '../db/pool';

const onlineUsers = new Map<string, { userId: string; username: string; socketCount: number }>();

function broadcastPresence(io: Server) {
  io.emit('presence:update', {
    count: onlineUsers.size,
    users: Array.from(onlineUsers.values()).map((u) => ({ userId: u.userId, username: u.username }))
  });
}

export function attachSocketHandlers(io: Server) {
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token as string | undefined;
    if (!token) return next(new Error('Missing auth token'));
    try {
      const payload = verifyToken(token);
      socket.data.userId = payload.userId;
      socket.data.username = payload.username;
      next();
    } catch {
      next(new Error('Invalid or expired token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const { userId, username } = socket.data as { userId: string; username: string };

    const existing = onlineUsers.get(userId);
    if (existing) existing.socketCount += 1;
    else onlineUsers.set(userId, { userId, username, socketCount: 1 });
    broadcastPresence(io);

    socket.join('global');
    socket.join(`user:${userId}`);

    socket.on('chat:send', async (payload: { channel?: string; body?: string }) => {
      const channel = (payload?.channel || 'global').slice(0, 64);
      const body = (payload?.body || '').trim().slice(0, 500);
      if (!body) return;
      const message = { id: uuidv4(), channel, senderId: userId, senderUsername: username, body, sentAt: new Date().toISOString() };
      try {
        await pool.query(
          'INSERT INTO chat_messages (id, channel, sender_id, sender_username, body) VALUES ($1, $2, $3, $4, $5)',
          [message.id, channel, userId, username, body]
        );
      } catch (err) {
        console.error('Failed to persist chat message', err);
      }
      io.to(channel).emit('chat:message', message);
    });

    socket.on('chat:join', (channel: string) => {
      if (typeof channel === 'string' && channel.length <= 64) socket.join(channel);
    });

    socket.on('disconnect', () => {
      const entry = onlineUsers.get(userId);
      if (entry) {
        entry.socketCount -= 1;
        if (entry.socketCount <= 0) onlineUsers.delete(userId);
      }
      broadcastPresence(io);
    });
  });
}
