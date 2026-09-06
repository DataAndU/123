/**
 * Static catalog of real-money coin packs. These IDs must match the
 * in-app product IDs you configure in the Google Play Console — this
 * project can't create those for you, only verify purchases against them.
 */
export const COIN_PRODUCTS: Record<string, number> = {
  coins_small: 500,
  coins_medium: 3000,
  coins_large: 7000,
  coins_mega: 16000
};

export function coinsForProduct(productId: string): number | null {
  return COIN_PRODUCTS[productId] ?? null;
}
