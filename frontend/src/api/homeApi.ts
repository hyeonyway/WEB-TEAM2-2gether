import type {HomeOverviewDto} from '../dto/homeDto';
import mockupData from '../mocks/mockup-data.json';
import {request} from './httpClient';
import {isMockApiEnabled} from './mockApiConfig';

export async function fetchHomeOverview():Promise<HomeOverviewDto>{
  if(isMockApiEnabled())return mockupData.home as HomeOverviewDto;
  return request<HomeOverviewDto>('/api/home');
}
