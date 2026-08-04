export type WalletSyncMessage = {
  type: 'WALLET_CHANGED';
};

const channelName = 'dbidding-wallet';
const listeners = new Set<() => void>();
let channel: BroadcastChannel | null = null;

function isWalletSyncMessage(value: unknown): value is WalletSyncMessage {
  return typeof value === 'object'
    && value !== null
    && (value as Partial<WalletSyncMessage>).type === 'WALLET_CHANGED';
}

function getChannel() {
  if (typeof BroadcastChannel === 'undefined') return null;
  if (channel) return channel;

  channel = new BroadcastChannel(channelName);
  channel.addEventListener('message', event => {
    if (!isWalletSyncMessage(event.data)) return;
    listeners.forEach(listener => listener());
  });
  return channel;
}

export function publishWalletChanged() {
  getChannel()?.postMessage({type: 'WALLET_CHANGED'} satisfies WalletSyncMessage);
}

export function subscribeWalletChanged(listener: () => void) {
  const activeChannel = getChannel();
  if (!activeChannel) return () => undefined;

  listeners.add(listener);
  return () => {
    listeners.delete(listener);
    if (listeners.size > 0 || channel !== activeChannel) return;
    activeChannel.close();
    channel = null;
  };
}
