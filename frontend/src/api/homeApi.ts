import type {HomeInsightDto,HomeMarketDto,HomeTopGainersDto} from '../dto/homeDto';
import mockupData from '../mocks/mockup-data.json';
import {request} from './httpClient';
import {isMockApiEnabled} from './mockApiConfig';

export async function fetchHomeInsights():Promise<HomeInsightDto[]>{
  if(isMockApiEnabled())return mockupData.home.insights as HomeInsightDto[];
  return request<HomeInsightDto[]>('/api/home/insights');
}

export async function fetchHomeMarket(days=30):Promise<HomeMarketDto>{
  if(isMockApiEnabled())return {
    marketSummary:mockupData.home.marketSummary,
    marketHistory:mockupData.home.marketHistory,
  };
  return request<HomeMarketDto>(`/api/home/market?days=${days}`);
}

export async function fetchHomeTopGainers(limit=5):Promise<HomeTopGainersDto>{
  if(isMockApiEnabled())return {
    topGainersTitle:mockupData.home.topGainersTitle,
    topGainers:mockupData.home.topGainers,
  };
  return request<HomeTopGainersDto>(`/api/home/top-gainers?limit=${limit}`);
}
