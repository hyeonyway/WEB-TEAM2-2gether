import {useQuery} from '@tanstack/react-query';
import {CalendarDays,Diamond,Flame,Info,LineChart,SlidersVertical,TrendingUp} from 'lucide-react';
import {useState} from 'react';
import {Header} from '../../components';
import type {HomeInsightDto,HomeMarketPointDto,HomeRankingDto} from '../../dto/homeDto';
import {homeQueries} from '../../queries/homeQueries';

function Insight({insight}:{insight:HomeInsightDto}){
  const Icon=insight.id==='RISING'?Flame:insight.id==='NEW_BIDS'?TrendingUp:Diamond;
  const openAuctions=()=>{window.location.href=`/auction?sort=${insight.sort}`};
  return <article className={`insight ${insight.id==='NEW_BIDS'?'rise':insight.id==='ACTIVE'?'volume':'fire'} insight-action`} role="link" tabIndex={0} onClick={openAuctions} onKeyDown={event=>(event.key==='Enter'||event.key===' ')&&openAuctions()}>
    <div className="insight-title"><span><Icon/></span><b>{insight.title}</b></div>
    <div className="insight-value"><strong>{insight.value}<small>종</small></strong>{insight.changeRate!==null&&<em>+{insight.changeRate.toFixed(1)}%</em>}</div>
    <p>{insight.note}</p>
  </article>;
}

function Chart({history}:{history:HomeMarketPointDto[]}){
  const maxPrice=Math.max(...history.map(point=>point.averagePrice),1);
  const minPrice=Math.min(...history.map(point=>point.averagePrice),0);
  const priceRange=Math.max(maxPrice-minPrice,1);
  const maxBids=Math.max(...history.map(point=>point.bidCount),1);
  const coordinates=history.map((point,index)=>{
    const x=history.length===1?0:index/(history.length-1)*650;
    const y=205-(point.averagePrice-minPrice)/priceRange*190;
    return `${x.toFixed(1)} ${y.toFixed(1)}`;
  });
  const path=coordinates.map((point,index)=>`${index?'L':'M'}${point}`).join(' ');

  return <div className="chart-wrap">
    <div className="axis left"><span>{Math.round(maxPrice/10000)}만</span><span>{Math.round((maxPrice+minPrice)/20000)}만</span><span>{Math.round(minPrice/10000)}만</span></div>
    <div className="bars">{history.map(point=><i key={point.date} style={{height:`${Math.max(18,point.bidCount/maxBids*90)}%`}}/>)}</div>
    <svg className="market-line" viewBox="0 0 650 220" preserveAspectRatio="none">
      <defs><linearGradient id="fade" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#19b86a" stopOpacity=".16"/><stop offset="1" stopColor="#19b86a" stopOpacity="0"/></linearGradient></defs>
      <path className="chart-area" d={`${path} L650 220 L0 220Z`}/><path className="chart-stroke" d={path}/>
    </svg>
    <div className="axis right"><span>{maxBids}건</span><span>{Math.round(maxBids/2)}건</span><span>0건</span></div>
    <div className="dates">{history.map(point=><span key={point.date}>{point.date}</span>)}</div>
    <div className="legend"><span className="green-line"/>경매가(원)<span className="gray-box"/>입찰량(건)</div>
  </div>;
}

function CardArt({theme}:{theme:string}){
  return <div className={`mini-card ${theme}`}><i>HP 70</i><span>●</span><small>POKÉMON</small></div>;
}

function Ranking({title,items}:{title:string;items:HomeRankingDto[]}){
  return <aside><h2>{title}</h2><div className="ranking">{items.map((item,index)=><a className="rank rank-action" key={item.auctionId} href={`/auction/${item.auctionId}`}>
    <b className="number">{index+1}</b><CardArt theme={item.theme}/>
    <div className="rank-info"><p>{item.name}</p><strong>{item.price.toLocaleString()}원 <em>+{item.changeRate.toFixed(1)}%</em></strong><small>입찰 {item.bidCount.toLocaleString()}건</small></div>
    <svg viewBox="0 0 60 42" preserveAspectRatio="none" aria-hidden="true"><path d="M2 37 L12 32 L22 34 L32 23 L42 18 L58 7"/></svg>
  </a>)}</div></aside>;
}

export default function HomePage(){
  const[mode,setMode]=useState<'line'|'bar'>('line');
  const{data,isPending,error}=useQuery(homeQueries.overview());

  return <><Header/><main>
    <div className="home-overview-row"><div><p className="intro">현재 진행 중인 카드 경매의 실시간 입찰 현황입니다.</p><div className="date"><CalendarDays/> 실시간 경매 기준</div></div></div>
    {isPending?<p className="catalog-count">홈 데이터를 불러오는 중…</p>:error||!data?<p className="form-error">홈 데이터를 불러오지 못했습니다.</p>:<>
      <div className="section-title-row"><h2 className="section-title">경매 인사이트</h2></div>
      <section className="insights">{data.insights.map(insight=><Insight key={insight.id} insight={insight}/>)}</section>
      <section className="dashboard"><div className="market"><h2>30일 경매가 · 입찰량</h2><div className="market-panel">
        <div className="metrics">
          <div><span>현재 경매가 평균 <Info/></span><strong>{data.marketSummary.currentPriceAverage.toLocaleString()}원</strong></div>
          <div><span>1일 상승</span><b>+{data.marketSummary.dailyChangeRate.toFixed(1)}%</b></div>
          <div><span>7일 상승</span><b>+{data.marketSummary.weeklyChangeRate.toFixed(1)}%</b></div>
          <div><span>30일 상승</span><b>+{data.marketSummary.monthlyChangeRate.toFixed(1)}%</b></div>
          <div><span>30일 총 입찰</span><strong>{data.marketSummary.monthlyBidCount.toLocaleString()}건</strong></div>
          <div className="switch"><button className={mode==='line'?'on':''} onClick={()=>setMode('line')} aria-label="경매가 그래프"><LineChart/></button><button className={mode==='bar'?'on':''} onClick={()=>setMode('bar')} aria-label="입찰량 그래프"><SlidersVertical/></button></div>
        </div>
        <Chart history={data.marketHistory}/>
      </div></div><Ranking title={data.topGainersTitle} items={data.topGainers}/></section>
    </>}
  </main><footer/></>;
}
