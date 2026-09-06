import { GoogleAuth } from 'google-auth-library';

export interface PlayVerificationResult {
  valid: boolean;
  reason?: string;
}

interface ProductPurchase {
  purchaseState?: number; // 0 = purchased, 1 = cancelled, 2 = pending
  consumptionState?: number;
}

let cachedAuth: GoogleAuth | null = null;

function getAuth(): GoogleAuth | null {
  const json = process.env.GOOGLE_SERVICE_ACCOUNT_JSON;
  if (!json) return null;
  if (!cachedAuth) {
    const credentials = JSON.parse(json);
    cachedAuth = new GoogleAuth({ credentials, scopes: ['https://www.googleapis.com/auth/androidpublisher'] });
  }
  return cachedAuth;
}

export function isPlayBillingConfigured(): boolean {
  return Boolean(process.env.GOOGLE_SERVICE_ACCOUNT_JSON && process.env.GOOGLE_PLAY_PACKAGE_NAME);
}

/** Local-testing-only bypass — never enable this in a real deployment. */
export function isBillingDevMode(): boolean {
  return process.env.BILLING_DEV_MODE === 'true';
}

/**
 * Verifies a purchase token against the real Google Play Developer API
 * (Android Publisher). Requires a service account with access to the Play
 * Console app granted "View financial data" + "Manage orders" permissions,
 * and its JSON key in GOOGLE_SERVICE_ACCOUNT_JSON. See README for setup —
 * this project cannot create that Play Console app or service account for
 * you.
 */
export async function verifyPlayPurchase(productId: string, purchaseToken: string): Promise<PlayVerificationResult> {
  const auth = getAuth();
  const packageName = process.env.GOOGLE_PLAY_PACKAGE_NAME;
  if (!auth || !packageName) {
    return { valid: false, reason: 'Play Billing verification is not configured on this server' };
  }

  try {
    const client = await auth.getClient();
    const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(packageName)}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;
    const res = await client.request<ProductPurchase>({ url });
    if (res.data.purchaseState === 0) return { valid: true };
    return { valid: false, reason: `Unexpected purchase state: ${res.data.purchaseState}` };
  } catch (err: any) {
    return { valid: false, reason: err?.message ?? 'Verification request failed' };
  }
}
