import type {HomeInsightDto,HomeMarketDto,HomePriceMoversDto,HomeTopGainersDto} from '../dto/homeDto';
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
  const monthlyWinningPriceTotal=marketHistory.reduce(
    (sum,point)=>sum+(point.averagePrice*Math.max(1,Math.round(point.bidCount/4))),
    0,
  );
  const monthlyEndedAuctionCount=marketHistory.reduce(
    (sum,point)=>sum+Math.max(1,Math.round(point.bidCount/4)),
    0,
  );
  const monthlyBidCount=marketHistory.reduce((sum,point)=>sum+point.bidCount,0);
  return {
    marketSummary:{
      monthlyWinningPriceTotal,
      monthlyEndedAuctionCount,
      monthlyBidCount,
      monthlyHighestPrice:Math.max(...marketHistory.map(point=>point.averagePrice)),
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
  const yesterday=new Date();
  yesterday.setDate(yesterday.getDate()-1);
  const previous=new Date(yesterday);
  previous.setDate(previous.getDate()-1);
  const response:HomeTopGainersDto=isMockApiEnabled()?{
    topGainersTitle:mockupData.home.topGainersTitle,
    topGainers:mockupData.home.topGainers.map(item=>({
      ...item,
      currentDate:yesterday.toISOString().slice(0,10),
      previousDate:previous.toISOString().slice(0,10),
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

export async function fetchHomePriceMovers(limit=5):Promise<HomePriceMoversDto>{
  const yesterday=new Date();
  yesterday.setDate(yesterday.getDate()-1);
  const previous=new Date(yesterday);
  previous.setDate(previous.getDate()-1);
  const isoDate=(date:Date)=>date.toISOString().slice(0,10);
  const source=mockupData.home.topGainers.slice(0,limit);
  const response:HomePriceMoversDto=isMockApiEnabled()?{
    periodDays:30,
    gainers:source.map(item=>({
      ...item,
      currentDate:isoDate(yesterday),
      previousDate:isoDate(previous),
      priceHistory:mockRankingHistory(item.price,item.changeRate,item.cardId),
    })),
    losers:source.map((item,index)=>{
      const changeRate=-(2.4+index*1.3);
      const price=Math.round(item.price*(.92-index*.015));
      return {
        ...item,
        cardId:item.cardId+100,
        name:`${item.name} 하락 매물`,
        price,
        changeRate,
        currentDate:isoDate(yesterday),
        previousDate:isoDate(previous),
        priceHistory:mockRankingHistory(price,changeRate,item.cardId+100),
      };
    }),
  }:await request<HomePriceMoversDto>(`/api/home/price-movers?limit=${limit}`);
  const resolveItems=(items:HomePriceMoversDto['gainers'])=>items.map(item=>({
    ...item,
    imageUrl:resolveImageUrl(item.imageUrl),
  }));
  return {...response,gainers:resolveItems(response.gainers),losers:resolveItems(response.losers)};
}
