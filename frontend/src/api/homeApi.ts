import type {HomeInsightDto,HomeMarketDto,HomeTopGainersDto} from '../dto/homeDto';
import mockupData from '../mocks/mockup-data.json';
import {request} from './httpClient';
import {resolveImageUrl} from './auctionMapper';
import {isMockApiEnabled} from './mockApiConfig';

const mockRankingHistory=(price:number,changeRate:number,cardId:number)=>{
  const yesterday=new Date();
  yesterday.setDate(yesterday.getDate()-1);
  const previousPrice=Math.max(1,Math.round(price/(1+changeRate/100)));
  const monthStartPrice=Math.max(1,Math.round(
    previousPrice*(1+((cardId%5)-2)*.025),
  ));
  return Array.from({length:30},(_,index)=>{
    const date=new Date(yesterday);
    date.setDate(yesterday.getDate()-(29-index));
    if(index===29)return {
      date:`${String(date.getMonth()+1).padStart(2,'0')}/${String(date.getDate()).padStart(2,'0')}`,
      price,
    };
    const progress=index/28;
    const wave=Math.sin((index+cardId)*.72)
      *Math.sin(Math.PI*progress)
      *price
      *.018;
    return {
      date:`${String(date.getMonth()+1).padStart(2,'0')}/${String(date.getDate()).padStart(2,'0')}`,
      price:index===28
        ?previousPrice
        :Math.max(1,Math.round(
          monthStartPrice+(previousPrice-monthStartPrice)*progress+wave,
        )),
    };
  });
};

const changeRate=(current:number,previous:number)=>
  previous<=0?0:Number(((current-previous)/previous*100).toFixed(2));

const mockMarket=(days:number):HomeMarketDto=>{
  const seoulParts=new Intl.DateTimeFormat('en-CA',{
    timeZone:'Asia/Seoul',
    year:'numeric',
    month:'2-digit',
    day:'2-digit',
  }).formatToParts(new Date());
  const part=(type:string)=>Number(seoulParts.find(item=>item.type===type)?.value);
  const today=Date.UTC(part('year'),part('month')-1,part('day'));
  const prices=Array.from({length:days+1},(_,index)=>Math.max(1,Math.round(
    168000+(index*2100)+Math.sin(index*.71)*14500+Math.cos(index*.29)*6500,
  )));
  const marketHistory=Array.from({length:days},(_,index)=>{
    const date=new Date(today-((days-index)*86400000));
    return {
      date:`${String(date.getUTCMonth()+1).padStart(2,'0')}/${String(date.getUTCDate()).padStart(2,'0')}`,
      averagePrice:prices[index+1],
      bidCount:Math.max(0,Math.round(
        120+Math.sin(index*.67)*68+Math.cos(index*.31)*34+(index%6)*11,
      )),
    };
  });
  const current=marketHistory.at(-1)?.averagePrice??0;
  const dailyBase=marketHistory.at(-2)?.averagePrice??prices[0];
  const weeklyBase=marketHistory.at(-8)?.averagePrice??prices[0];
  return {
    marketSummary:{
      currentPriceAverage:current,
      dailyChangeRate:changeRate(current,dailyBase),
      weeklyChangeRate:changeRate(current,weeklyBase),
      monthlyChangeRate:changeRate(current,prices[0]),
      monthlyBidCount:marketHistory.reduce((sum,point)=>sum+point.bidCount,0),
    },
    marketHistory,
  };
};

export async function fetchHomeInsights():Promise<HomeInsightDto[]>{
  if(isMockApiEnabled())return mockupData.home.insights as HomeInsightDto[];
  return request<HomeInsightDto[]>('/api/home/insights');
}

export async function fetchHomeMarket(days=30):Promise<HomeMarketDto>{
  if(isMockApiEnabled())return mockMarket(days);
  return request<HomeMarketDto>(`/api/home/market?days=${days}`);
}

export async function fetchHomeTopGainers(limit=5):Promise<HomeTopGainersDto>{
  const response:HomeTopGainersDto=isMockApiEnabled()?{
    topGainersTitle:mockupData.home.topGainersTitle,
    topGainers:mockupData.home.topGainers.map(item=>({
      ...item,
      priceHistory:mockRankingHistory(item.price,item.changeRate,item.cardId),
    })),
  }:await request<HomeTopGainersDto>(`/api/home/top-gainers?limit=${limit}`);
  return {
    ...response,
    topGainers:response.topGainers.map(item=>({
      ...item,
      imageUrl:resolveImageUrl(item.imageUrl),
    })),
  };
}
