import {Route, Routes} from 'react-router-dom';
import {Header} from '../components';
import AuctionDetailPage from '../pages/auction-detail';
import AuctionPage from '../pages/auction';
import CardDetailPage from '../pages/card-detail';
import CardsPage from '../pages/cards';
import DashboardPage from '../pages/dashboard';
import HomePage from '../pages/home';
import MyPage from '../pages/mypage';
import SellPage from '../pages/sell';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage/>}/>
      <Route path="/cards" element={<CardsPage/>}/>
      <Route path="/cards/:cardId" element={<CardDetailPage/>}/>
      <Route path="/auction" element={<AuctionPage/>}/>
      <Route path="/auction/:auctionId" element={<AuctionDetailPage/>}/>
      <Route path="/dashboard" element={<DashboardPage/>}/>
      <Route path="/mypage" element={<MyPage/>}/>
      <Route path="/sell" element={<SellPage Header={Header}/>}/>
      <Route path="*" element={<HomePage/>}/>
    </Routes>
  );
}
