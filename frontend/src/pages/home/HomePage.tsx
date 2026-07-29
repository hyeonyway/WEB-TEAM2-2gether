import {useQuery} from '@tanstack/react-query';
import {CalendarDays,Diamond,Flame,Info,TrendingUp} from 'lucide-react';
import {useEffect,useState} from 'react';
import {Area,Bar,CartesianGrid,ComposedChart,Legend,Line,XAxis,YAxis} from 'recharts';
import {Header} from '../../components';
import {ChartContainer,ChartTooltip,ChartTooltipContent} from '../../components/ui/chart';
import type {HomeInsightDto,HomeMarketPointDto,HomeRankingDto} from '../../dto/homeDto';
import {homeQueries} from '../../queries/homeQueries';

function AnimatedNumber({value,suffix}:{value:number;suffix:'원'|'건'}){
  const[displayValue,setDisplayValue]=useState(0);

  useEffect(()=>{
    if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){
      setDisplayValue(value);
      return;
    }

    let animationFrame=0;
    const duration=1100;
    const startedAt=performance.now();
    const animate=(now:number)=>{
      const progress=Math.min((now-startedAt)/duration,1);
      const eased=1-Math.pow(1-progress,3);
      setDisplayValue(Math.round(value*eased));
      if(progress<1)animationFrame=requestAnimationFrame(animate);
    };
    animationFrame=requestAnimationFrame(animate);
    return()=>cancelAnimationFrame(animationFrame);
  },[value]);

  return <>{displayValue.toLocaleString()}<small>{suffix}</small></>;
}

function InsightsSkeleton(){
  return <section className="insights home-skeleton" aria-label="경매 인사이트를 불러오는 중" aria-busy="true">
    {Array.from({length:3},(_,index)=><article className="insight home-insight-skeleton" key={index}>
      <div className="home-skeleton-heading"><i className="skeleton-pulse"/><span className="skeleton-pulse"/></div>
      <b className="skeleton-pulse"/>
      <small className="skeleton-pulse"/>
    </article>)}
  </section>;
}

function MarketSkeleton(){
  return <div className="market-panel home-market-skeleton home-skeleton" aria-label="경매 통계를 불러오는 중" aria-busy="true">
    <div className="home-market-skeleton-metrics">
      {Array.from({length:4},(_,index)=><div key={index}>
        <i className="skeleton-pulse"/>
        <b className="skeleton-pulse"/>
      </div>)}
    </div>
    <div className="home-chart-skeleton">
      <i className="home-chart-grid-line"/><i className="home-chart-grid-line"/><i className="home-chart-grid-line"/>
      <div>{Array.from({length:18},(_,index)=><span className="skeleton-pulse" style={{height:`${22+(index*17)%58}%`}} key={index}/>)}</div>
    </div>
  </div>;
}

function RankingSkeleton(){
  return <aside className="home-skeleton"><h2>이전 가격 대비</h2>
    <div className="ranking home-ranking-skeleton" aria-label="상승 순위를 불러오는 중" aria-busy="true">
      {Array.from({length:5},(_,index)=><div className="home-rank-skeleton" key={index}>
        <i className="skeleton-pulse"/><span className="skeleton-pulse"/>
        <div><b className="skeleton-pulse"/><small className="skeleton-pulse"/></div>
        <em className="skeleton-pulse"/>
      </div>)}
    </div>
  </aside>;
}

function Insight({insight}:{insight:HomeInsightDto}){
  const Icon=insight.id==='RISING'?Flame:insight.id==='NEW_BIDS'?TrendingUp:Diamond;
  const openAuctions=()=>{window.location.href=`/auction?sort=${insight.sort}`};
  return <article className={`insight ${insight.id==='NEW_BIDS'?'rise':insight.id==='ACTIVE'?'volume':'fire'} insight-action`} role="link" tabIndex={0} onClick={openAuctions} onKeyDown={event=>(event.key==='Enter'||event.key===' ')&&openAuctions()}>
    <div className="insight-title"><span><Icon/></span><b>{insight.title}</b></div>
    <div className="insight-value"><strong><AnimatedNumber value={insight.value} suffix="건"/></strong>{insight.changeRate!==null&&<em>+{insight.changeRate.toFixed(1)}%</em>}</div>
    <p>{insight.note}</p>
  </article>;
}

function Chart({history}:{history:HomeMarketPointDto[]}){
  const config={
    averagePrice:{label:'시세(원)',color:'#ff584d'},
    bidCount:{label:'입찰량(건)',color:'#e2e2e2'},
  };
  const tickInterval=Math.max(Math.ceil(history.length/6)-1,0);

  return <ChartContainer config={config} className="home-market-chart">
    <ComposedChart data={history} margin={{top:16,right:8,bottom:4,left:8}} accessibilityLayer>
      <defs>
        <linearGradient id="home-price-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="5%" stopColor="var(--color-averagePrice)" stopOpacity={0.22}/>
          <stop offset="95%" stopColor="var(--color-averagePrice)" stopOpacity={0.01}/>
        </linearGradient>
      </defs>
      <CartesianGrid vertical={false} stroke="#edf0ee" strokeDasharray="3 3"/>
      <XAxis dataKey="date" interval={tickInterval} axisLine={false} tickLine={false} tick={{fill:'#a1a6a3',fontSize:10}}/>
      <YAxis
        yAxisId="price"
        axisLine={false}
        tickLine={false}
        tick={{fill:'#a1a6a3',fontSize:10}}
        tickFormatter={value=>`${Math.round(Number(value)/10000)}만`}
        width={42}
      />
      <YAxis
        yAxisId="bids"
        orientation="right"
        axisLine={false}
        tickLine={false}
        tick={{fill:'#a1a6a3',fontSize:10}}
        tickFormatter={value=>`${value}건`}
        width={42}
      />
      <ChartTooltip
        cursor={{stroke:'#cfd5d1',strokeDasharray:'3 3'}}
        content={<ChartTooltipContent labelFormatter={label=>`${label} 경매 통계`}/>}
      />
      <Legend
        verticalAlign="bottom"
        content={()=><div className="home-chart-legend">
          <span><i className="price-line"/>시세(원)</span>
          <span><i className="bid-square"/>입찰량(건)</span>
        </div>}
      />
      <Bar
        yAxisId="bids"
        dataKey="bidCount"
        fill="var(--color-bidCount)"
        radius={[4,4,0,0]}
        maxBarSize={10}
      />
      <Area
        yAxisId="price"
        type="monotone"
        dataKey="averagePrice"
        stroke="none"
        fill="url(#home-price-fill)"
        dot={false}
        activeDot={false}
      />
      <Line
        yAxisId="price"
        type="monotone"
        dataKey="averagePrice"
        stroke="var(--color-averagePrice)"
        strokeWidth={2.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
        dot={false}
        activeDot={{r:5,fill:'#fff',stroke:'var(--color-averagePrice)',strokeWidth:3}}
      />
    </ComposedChart>
  </ChartContainer>;
}

function CardArt({theme}:{theme:string}){
  return <div className={`mini-card ${theme}`}><i>HP 70</i><span>●</span><small>POKÉMON</small></div>;
}

function CardThumbnail({item}:{item:HomeRankingDto}){
  const[failed,setFailed]=useState(false);
  if(!item.imageUrl||failed)return <CardArt theme={item.theme}/>;
  return <img
    className="rank-card-image"
    src={item.imageUrl}
    alt=""
    loading="lazy"
    onError={()=>setFailed(true)}
  />;
}

function PriceSparkline({history,direction}:{history:HomeRankingDto['priceHistory'];direction:'gain'|'loss'}){
  if(!history.length)return <svg viewBox="0 0 60 42" aria-label="시세 이력 없음"/>;
  const prices=history.map(point=>point.price);
  const min=Math.min(...prices);
  const max=Math.max(...prices);
  const range=Math.max(max-min,1);
  const points=history.map((point,index)=>{
    const x=history.length===1?30:2+index/(history.length-1)*56;
    const y=37-(point.price-min)/range*30;
    return `${x.toFixed(1)} ${y.toFixed(1)}`;
  });
  const path=points.map((point,index)=>`${index?'L':'M'}${point}`).join(' ');
  const first=history[0];
  const last=history[history.length-1];
  return <svg className={direction} viewBox="0 0 60 42" role="img" aria-label={`${first.date}부터 ${last.date}까지 실제 시세 추이`}>
    <path d={path}/>
  </svg>;
}

function Ranking({gainers,losers}:{gainers:HomeRankingDto[];losers:HomeRankingDto[]}){
  const[tab,setTab]=useState<'gain'|'loss'>('gain');
  const items=tab==='gain'?gainers:losers;
  return <aside><div className="mover-heading"><h2>이전 가격 대비</h2><div className="mover-tabs" role="tablist" aria-label="가격 변동 순위">
    <button className={tab==='gain'?'active':''} role="tab" aria-selected={tab==='gain'} onClick={()=>setTab('gain')}>상승 TOP5</button>
    <button className={tab==='loss'?'active loss':''} role="tab" aria-selected={tab==='loss'} onClick={()=>setTab('loss')}>하락 TOP5</button>
  </div></div>
  {items.length===0?<div className="ranking ranking-empty">최근 30일 내 비교 가능한 {tab==='gain'?'상승':'하락'} 카드가 없습니다.</div>
    :<div className="ranking">{items.map((item,index)=><a className={`rank rank-action ${tab}`} key={item.cardId} href={`/cards/${item.cardId}`}>
    <b className="number">{index+1}</b><CardThumbnail item={item}/>
    <div className="rank-info"><p>{item.name}</p><strong>{item.price.toLocaleString()}원 <em>{item.changeRate>0?'+':''}{item.changeRate.toFixed(1)}%</em></strong><small>최근 거래일 입찰 {item.bidCount.toLocaleString()}건</small></div>
    <PriceSparkline history={item.priceHistory} direction={tab}/>
  </a>)}</div>}</aside>;
}

export default function HomePage(){
  const insightsQuery=useQuery(homeQueries.insights());
  const marketQuery=useQuery(homeQueries.market(30));
  const priceMoversQuery=useQuery(homeQueries.priceMovers(5));

  return <><Header/><main>
    <div className="home-overview-row"><div><p className="intro">현재 진행 중인 카드 경매의 실시간 입찰 현황입니다.</p><div className="date"><CalendarDays/> 실시간 경매 기준</div></div></div>
    <div className="section-title-row"><h2 className="section-title">경매 인사이트</h2></div>
    {insightsQuery.isPending?<InsightsSkeleton/>
      :insightsQuery.error||!insightsQuery.data?<p className="form-error">경매 인사이트를 불러오지 못했습니다.</p>
        :<section className="insights">{insightsQuery.data.map(insight=><Insight key={insight.id} insight={insight}/>)}</section>}
    <section className="dashboard"><div className="market"><h2>30일 경매가 · 입찰량</h2>
      {marketQuery.isPending?<MarketSkeleton/>
        :marketQuery.error||!marketQuery.data?<p className="form-error">경매 통계를 불러오지 못했습니다.</p>
          :<div className="market-panel">
        <div className="metrics">
          <div><span>30일 낙찰가 총합 <Info/></span><strong><AnimatedNumber value={marketQuery.data.marketSummary.monthlyWinningPriceTotal} suffix="원"/></strong></div>
          <div><span>30일 총 낙찰</span><strong><AnimatedNumber value={marketQuery.data.marketSummary.monthlyEndedAuctionCount} suffix="건"/></strong></div>
          <div><span>30일 총 입찰</span><strong><AnimatedNumber value={marketQuery.data.marketSummary.monthlyBidCount} suffix="건"/></strong></div>
          <div><span>30일 최고 낙찰가</span><strong><AnimatedNumber value={marketQuery.data.marketSummary.monthlyHighestPrice} suffix="원"/></strong></div>
        </div>
        <Chart history={marketQuery.data.marketHistory}/>
      </div>}</div>
      {priceMoversQuery.isPending?<RankingSkeleton/>
        :priceMoversQuery.error||!priceMoversQuery.data?<aside><h2>이전 가격 대비</h2><p className="form-error">순위를 불러오지 못했습니다.</p></aside>
          :<Ranking gainers={priceMoversQuery.data.gainers} losers={priceMoversQuery.data.losers}/>}
    </section>
  </main><footer/></>;
}
