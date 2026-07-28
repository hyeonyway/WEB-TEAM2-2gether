import {useQuery} from '@tanstack/react-query';
import {CalendarDays,Diamond,Flame,Info,TrendingUp} from 'lucide-react';
import {Area,Bar,CartesianGrid,ComposedChart,Legend,XAxis,YAxis} from 'recharts';
import {Header} from '../../components';
import {ChartContainer,ChartTooltip,ChartTooltipContent} from '../../components/ui/chart';
import type {HomeInsightDto,HomeMarketPointDto,HomeRankingDto} from '../../dto/homeDto';
import {homeQueries} from '../../queries/homeQueries';

function Insight({insight}:{insight:HomeInsightDto}){
  const Icon=insight.id==='RISING'?Flame:insight.id==='NEW_BIDS'?TrendingUp:Diamond;
  const openAuctions=()=>{window.location.href=`/auction?sort=${insight.sort}`};
  return <article className={`insight ${insight.id==='NEW_BIDS'?'rise':insight.id==='ACTIVE'?'volume':'fire'} insight-action`} role="link" tabIndex={0} onClick={openAuctions} onKeyDown={event=>(event.key==='Enter'||event.key===' ')&&openAuctions()}>
    <div className="insight-title"><span><Icon/></span><b>{insight.title}</b></div>
    <div className="insight-value"><strong>{insight.value}<small>건</small></strong>{insight.changeRate!==null&&<em>+{insight.changeRate.toFixed(1)}%</em>}</div>
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
        stroke="var(--color-averagePrice)"
        strokeWidth={2.8}
        fill="url(#home-price-fill)"
        dot={false}
        activeDot={{r:5,fill:'#fff',stroke:'var(--color-averagePrice)',strokeWidth:3}}
      />
    </ComposedChart>
  </ChartContainer>;
}

function CardArt({theme}:{theme:string}){
  return <div className={`mini-card ${theme}`}><i>HP 70</i><span>●</span><small>POKÉMON</small></div>;
}

function Ranking({title,items}:{title:string;items:HomeRankingDto[]}){
  return <aside><h2>{title}</h2><div className="ranking">{items.map((item,index)=><a className="rank rank-action" key={item.cardId} href={`/cards/${item.cardId}`}>
    <b className="number">{index+1}</b><CardArt theme={item.theme}/>
    <div className="rank-info"><p>{item.name}</p><strong>{item.price.toLocaleString()}원 <em>+{item.changeRate.toFixed(1)}%</em></strong><small>입찰 {item.bidCount.toLocaleString()}건</small></div>
    <svg viewBox="0 0 60 42" preserveAspectRatio="none" aria-hidden="true"><path d="M2 37 L12 32 L22 34 L32 23 L42 18 L58 7"/></svg>
  </a>)}</div></aside>;
}

export default function HomePage(){
  const insightsQuery=useQuery(homeQueries.insights());
  const marketQuery=useQuery(homeQueries.market(30));
  const topGainersQuery=useQuery(homeQueries.topGainers(5));

  return <><Header/><main>
    <div className="home-overview-row"><div><p className="intro">현재 진행 중인 카드 경매의 실시간 입찰 현황입니다.</p><div className="date"><CalendarDays/> 실시간 경매 기준</div></div></div>
    <div className="section-title-row"><h2 className="section-title">경매 인사이트</h2></div>
    {insightsQuery.isPending?<p className="catalog-count">경매 인사이트를 불러오는 중…</p>
      :insightsQuery.error||!insightsQuery.data?<p className="form-error">경매 인사이트를 불러오지 못했습니다.</p>
        :<section className="insights">{insightsQuery.data.map(insight=><Insight key={insight.id} insight={insight}/>)}</section>}
    <section className="dashboard"><div className="market"><h2>30일 경매가 · 입찰량</h2>
      {marketQuery.isPending?<p className="catalog-count">경매 통계를 불러오는 중…</p>
        :marketQuery.error||!marketQuery.data?<p className="form-error">경매 통계를 불러오지 못했습니다.</p>
          :<div className="market-panel">
        <div className="metrics">
          <div><span>현재 경매가 평균 <Info/></span><strong>{marketQuery.data.marketSummary.currentPriceAverage.toLocaleString()}원</strong></div>
          <div><span>1일 상승</span><b>{marketQuery.data.marketSummary.dailyChangeRate>0?'+':''}{marketQuery.data.marketSummary.dailyChangeRate.toFixed(1)}%</b></div>
          <div><span>7일 상승</span><b>{marketQuery.data.marketSummary.weeklyChangeRate>0?'+':''}{marketQuery.data.marketSummary.weeklyChangeRate.toFixed(1)}%</b></div>
          <div><span>30일 상승</span><b>{marketQuery.data.marketSummary.monthlyChangeRate>0?'+':''}{marketQuery.data.marketSummary.monthlyChangeRate.toFixed(1)}%</b></div>
          <div><span>30일 총 입찰</span><strong>{marketQuery.data.marketSummary.monthlyBidCount.toLocaleString()}건</strong></div>
        </div>
        <Chart history={marketQuery.data.marketHistory}/>
      </div>}</div>
      {topGainersQuery.isPending?<aside><h2>전일 상승 Top 5</h2><p className="catalog-count">순위를 불러오는 중…</p></aside>
        :topGainersQuery.error||!topGainersQuery.data?<aside><h2>전일 상승 Top 5</h2><p className="form-error">순위를 불러오지 못했습니다.</p></aside>
          :<Ranking title={topGainersQuery.data.topGainersTitle} items={topGainersQuery.data.topGainers}/>}
    </section>
  </main><footer/></>;
}
