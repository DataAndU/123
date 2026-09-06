import { Router } from 'express';
import { Server } from 'socket.io';
import { z } from 'zod';
import { requireAuth } from '../middleware/auth';
import { asyncHandler, HttpError } from '../util/asyncHandler';
import {
  createListing,
  getListing,
  listActiveListings,
  listMyListings,
  listOrdersForBuyer,
  listOrdersForSeller,
  updateListing
} from '../marketplace/repository';
import { advanceOrderStatus, cancelOrder, placeOrder } from '../marketplace/orders';
import { getBusiness } from '../business/repository';

const createListingSchema = z.object({
  businessId: z.string().uuid().nullable().optional(),
  title: z.string().min(1).max(80),
  description: z.string().max(500).default(''),
  priceCoins: z.number().int().positive(),
  quantity: z.number().int().positive()
});

const orderSchema = z.object({ listingId: z.string().uuid(), quantity: z.number().int().positive() });
const advanceSchema = z.object({ status: z.enum(['accepted', 'producing', 'delivered']) });

export function createMarketplaceRouter(io: Server): Router {
  const router = Router();
  router.use(requireAuth);

  router.get(
    '/listings',
    asyncHandler(async (req, res) => {
      res.json(await listActiveListings(req.userId));
    })
  );

  router.get(
    '/listings/mine',
    asyncHandler(async (req, res) => {
      res.json(await listMyListings(req.userId!));
    })
  );

  router.post(
    '/listings',
    asyncHandler(async (req, res) => {
      const parsed = createListingSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, parsed.error.issues.map((i) => i.message).join('; '));
      const { businessId, title, description, priceCoins, quantity } = parsed.data;
      if (businessId) {
        const business = await getBusiness(businessId);
        if (!business || business.userId !== req.userId) throw new HttpError(403, 'Not your business');
      }
      const listing = await createListing(req.userId!, businessId ?? null, title, description, priceCoins, quantity);
      res.status(201).json(listing);
    })
  );

  const updateListingSchema = z.object({ status: z.enum(['active', 'paused', 'closed']).optional(), priceCoins: z.number().int().positive().optional() });
  router.patch(
    '/listings/:id',
    asyncHandler(async (req, res) => {
      const listing = await getListing(req.params.id);
      if (!listing) throw new HttpError(404, 'Listing not found');
      if (listing.sellerId !== req.userId) throw new HttpError(403, 'Not your listing');
      const parsed = updateListingSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, 'Invalid update');
      const fields: Record<string, unknown> = {};
      if (parsed.data.status) fields.status = parsed.data.status;
      if (parsed.data.priceCoins) fields.price_coins = parsed.data.priceCoins;
      await updateListing(listing.id, fields);
      res.json({ ok: true });
    })
  );

  router.post(
    '/orders',
    asyncHandler(async (req, res) => {
      const parsed = orderSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, 'Invalid order request');
      const order = await placeOrder(req.userId!, parsed.data.listingId, parsed.data.quantity);
      io.to(`user:${order.sellerId}`).emit('marketplace:order_placed', order);
      res.status(201).json(order);
    })
  );

  router.get(
    '/orders/buying',
    asyncHandler(async (req, res) => {
      res.json(await listOrdersForBuyer(req.userId!));
    })
  );

  router.get(
    '/orders/selling',
    asyncHandler(async (req, res) => {
      res.json(await listOrdersForSeller(req.userId!));
    })
  );

  router.post(
    '/orders/:id/advance',
    asyncHandler(async (req, res) => {
      const parsed = advanceSchema.safeParse(req.body);
      if (!parsed.success) throw new HttpError(400, 'Invalid status');
      const order = await advanceOrderStatus(req.userId!, req.params.id, parsed.data.status);
      io.to(`user:${order.buyerId}`).emit('marketplace:order_updated', order);
      res.json(order);
    })
  );

  router.post(
    '/orders/:id/cancel',
    asyncHandler(async (req, res) => {
      const order = await cancelOrder(req.userId!, req.params.id);
      io.to(`user:${order.buyerId}`).emit('marketplace:order_updated', order);
      io.to(`user:${order.sellerId}`).emit('marketplace:order_updated', order);
      res.json(order);
    })
  );

  return router;
}
