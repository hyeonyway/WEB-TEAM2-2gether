import type {HomeOverviewDto} from '../dto/homeDto';
import mockupData from '../mocks/mockup-data.json';
import {request} from './httpClient';

const useMock=import.meta.env.VITE_USE_MOCK_API==='true';

export async function fetchHomeOverview():Promise<HomeOverviewDto>{
  if(useMock)return mockupData.home as HomeOverviewDto;
  return request<HomeOverviewDto>('/api/home');
}
