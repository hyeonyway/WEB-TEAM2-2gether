import React from 'react';
import {createRoot} from 'react-dom/client';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import './tailwind.css';
import HomePage from './pages/home';
import AuctionPage from './pages/auction';
import AuctionDetailPage from './pages/auction-detail';
import CardsPage from './pages/cards';
import CardDetailPage from './pages/card-detail';
import DashboardPage from './pages/dashboard';
import MyPage from './pages/mypage';
import SellPage from './pages/sell';
import {Header} from './components';

const path=window.location.pathname;
const queryClient=new QueryClient({
  defaultOptions:{queries:{retry:1,refetchOnWindowFocus:false}},
});
const isCardDetail=/^\/cards\/[^/]+\/?$/.test(path);
const isAuctionDetail=/^\/auction\/[^/]+\/?$/.test(path);

const page=path==='/sell'||path==='/sell/'
  ? <SellPage Header={Header}/>
  : path==='/dashboard'||path==='/dashboard/'
    ? <DashboardPage/>
    : path==='/mypage'||path==='/mypage/'
      ? <MyPage/>
      : isCardDetail
        ? <CardDetailPage/>
        : isAuctionDetail
          ? <AuctionDetailPage/>
          : path==='/cards'||path==='/cards/'
            ? <CardsPage/>
            : path==='/auction'||path==='/auction/'
              ? <AuctionPage/>
              : <HomePage/>;

createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={queryClient}>{page}</QueryClientProvider>
);
