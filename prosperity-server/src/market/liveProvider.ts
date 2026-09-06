/**
 * Optional real-world data feeds. Everything here is inert unless the
 * corresponding env vars are set (see .env.example) — with no API key
 * configured, callers get `null`/empty results and the caller falls back
 * to the pure simulator. Both APIs used here have free tiers; you provide
 * your own key, this project never ships one.
 */

interface AlphaVantageGlobalQuote {
  'Global Quote'?: { '05. price'?: string };
}

export async function fetchAlphaVantageQuote(symbol: string, apiKey: string): Promise<number | null> {
  const url = `https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${encodeURIComponent(symbol)}&apikey=${encodeURIComponent(apiKey)}`;
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const json = (await res.json()) as AlphaVantageGlobalQuote;
    const priceStr = json['Global Quote']?.['05. price'];
    const price = priceStr ? parseFloat(priceStr) : NaN;
    return Number.isFinite(price) ? price : null;
  } catch (err) {
    console.error(`AlphaVantage quote fetch failed for ${symbol}`, err);
    return null;
  }
}

/**
 * Fetches quotes for each ticker sequentially with a delay, to stay under
 * AlphaVantage's free-tier rate limit (5 requests/minute). With 6 stocks
 * this takes about a minute — fine for a monthly tick, not for anything
 * faster.
 */
export async function fetchLiveStockQuotes(
  tickers: { instrumentId: string; realTicker: string | null }[],
  apiKey: string
): Promise<Record<string, number>> {
  const result: Record<string, number> = {};
  for (const t of tickers) {
    if (!t.realTicker) continue;
    const price = await fetchAlphaVantageQuote(t.realTicker, apiKey);
    if (price != null) result[t.instrumentId] = price;
    await new Promise((resolve) => setTimeout(resolve, 12_500));
  }
  return result;
}

interface FredObservation {
  date: string;
  value: string;
}
interface FredResponse {
  observations?: FredObservation[];
}

async function fetchFredObservations(seriesId: string, apiKey: string, limit: number): Promise<FredObservation[]> {
  const url = `https://api.stlouisfed.org/fred/series/observations?series_id=${seriesId}&api_key=${apiKey}&file_type=json&sort_order=desc&limit=${limit}`;
  const res = await fetch(url);
  if (!res.ok) return [];
  const json = (await res.json()) as FredResponse;
  return json.observations ?? [];
}

export interface LiveMacroIndicators {
  unemploymentRate?: number;
  inflationYoYPercent?: number;
  fedFundsRatePercent?: number;
}

/** Pulls real US unemployment, YoY CPI inflation, and the federal funds rate from FRED. */
export async function fetchLiveMacroIndicators(apiKey: string): Promise<LiveMacroIndicators> {
  const result: LiveMacroIndicators = {};
  try {
    const [unemployment, cpi, fedFunds] = await Promise.all([
      fetchFredObservations('UNRATE', apiKey, 1),
      fetchFredObservations('CPIAUCSL', apiKey, 13),
      fetchFredObservations('FEDFUNDS', apiKey, 1)
    ]);

    const unemploymentValue = parseFloat(unemployment[0]?.value);
    if (Number.isFinite(unemploymentValue)) result.unemploymentRate = unemploymentValue;

    const fedFundsValue = parseFloat(fedFunds[0]?.value);
    if (Number.isFinite(fedFundsValue)) result.fedFundsRatePercent = fedFundsValue;

    if (cpi.length >= 13) {
      const latest = parseFloat(cpi[0].value);
      const yearAgo = parseFloat(cpi[12].value);
      if (Number.isFinite(latest) && Number.isFinite(yearAgo) && yearAgo !== 0) {
        result.inflationYoYPercent = ((latest - yearAgo) / yearAgo) * 100;
      }
    }
  } catch (err) {
    console.error('FRED macro data fetch failed', err);
  }
  return result;
}

export function isLiveDataConfigured(): boolean {
  return process.env.MARKET_DATA_PROVIDER === 'alphavantage' && Boolean(process.env.ALPHAVANTAGE_API_KEY);
}

export function isLiveMacroConfigured(): boolean {
  return Boolean(process.env.FRED_API_KEY);
}
