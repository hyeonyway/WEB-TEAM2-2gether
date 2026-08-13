export const routePaths = {
  home: '/',
  cards: '/cards',
  cardDetail: '/cards/:cardId',
  auction: '/auction',
  auctionDetail: '/auction/:auctionId',
  dashboard: '/dashboard',
  myPage: '/mypage',
  sell: '/sell',
  streamRecovery: '/admin/stream-recovery',
} as const;

export const appRoutePatterns = Object.values(routePaths);
