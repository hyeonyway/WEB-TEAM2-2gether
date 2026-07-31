import {Route, Routes} from 'react-router-dom';
import {Header} from '../components';
import {RequireAuth} from '../auth/RequireAuth';
import AuctionDetailPage from '../pages/auction-detail';
import AuctionPage from '../pages/auction';
import CardDetailPage from '../pages/card-detail';
import CardsPage from '../pages/cards';
import DashboardPage from '../pages/dashboard';
import HomePage from '../pages/home';
import MyPage from '../pages/mypage';
import SellPage from '../pages/sell';
import {routePaths} from './routePaths';

export function AppRoutes() {
  return (
    <Routes>
      <Route path={routePaths.home} element={<HomePage/>}/>
      <Route path={routePaths.cards} element={<CardsPage/>}/>
      <Route path={routePaths.cardDetail} element={<CardDetailPage/>}/>
      <Route path={routePaths.auction} element={<AuctionPage/>}/>
      <Route path={routePaths.auctionDetail} element={<AuctionDetailPage/>}/>
      <Route path={routePaths.dashboard} element={<RequireAuth><DashboardPage/></RequireAuth>}/>
      <Route path={routePaths.myPage} element={<RequireAuth><MyPage/></RequireAuth>}/>
      <Route path={routePaths.sell} element={<SellPage Header={Header}/>}/>
      <Route path="*" element={<HomePage/>}/>
    </Routes>
  );
}
