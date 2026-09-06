import dotenv from 'dotenv';
dotenv.config();

import express, { ErrorRequestHandler } from 'express';
import cors from 'cors';
import http from 'http';
import { Server } from 'socket.io';
import { authRouter } from './routes/auth';
import { playerRouter } from './routes/player';
import { socialRouter } from './routes/social';
import { marketRouter } from './routes/market';
import { createTradingRouter } from './routes/trading';
import { businessRouter } from './routes/business';
import { createMarketplaceRouter } from './routes/marketplace';
import { walletRouter } from './routes/wallet';
import { billingRouter } from './routes/billing';
import { HttpError } from './util/asyncHandler';
import { attachSocketHandlers } from './realtime/socket';
import { seedMarketsIfEmpty } from './market/model';
import { startTickScheduler } from './tick/scheduler';

const app = express();
app.use(cors());
app.use(express.json());

app.get('/health', (_req, res) => res.json({ ok: true, time: new Date().toISOString() }));

const httpServer = http.createServer(app);
const io = new Server(httpServer, { cors: { origin: '*' } });
attachSocketHandlers(io);

app.use('/auth', authRouter);
app.use('/player', playerRouter);
app.use('/social', socialRouter);
app.use('/market', marketRouter);
app.use('/trade', createTradingRouter(io));
app.use('/business', businessRouter);
app.use('/marketplace', createMarketplaceRouter(io));
app.use('/wallet', walletRouter);
app.use('/billing', billingRouter);

const errorHandler: ErrorRequestHandler = (err, _req, res, _next) => {
  if (err instanceof HttpError) {
    res.status(err.status).json({ error: err.message });
    return;
  }
  console.error(err);
  res.status(500).json({ error: 'Internal server error' });
};
app.use(errorHandler);

const port = Number(process.env.PORT) || 4000;
seedMarketsIfEmpty()
  .then(() => {
    httpServer.listen(port, () => {
      console.log(`Prosperity server listening on :${port}`);
      startTickScheduler(io);
    });
  })
  .catch((err) => {
    console.error('Failed to seed markets', err);
    process.exit(1);
  });
