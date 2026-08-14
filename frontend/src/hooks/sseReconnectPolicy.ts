export const SSE_RECONNECT_BASE_DELAY_MS=2_000;
export const SSE_RECONNECT_MAX_DELAY_MS=30_000;
export const SSE_REVALIDATE_AFTER_FAILURES=5;

export function nextSseReconnectDelayMs(consecutiveFailures:number):number{
  const exponent=Math.max(0,consecutiveFailures-1);
  return Math.min(SSE_RECONNECT_BASE_DELAY_MS*2**exponent,SSE_RECONNECT_MAX_DELAY_MS);
}

export function shouldRevalidateSession(consecutiveFailures:number):boolean{
  return consecutiveFailures>0&&consecutiveFailures%SSE_REVALIDATE_AFTER_FAILURES===0;
}
