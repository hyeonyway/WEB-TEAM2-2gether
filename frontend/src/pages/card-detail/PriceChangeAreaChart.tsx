import {useMemo} from 'react';
import {Area,Bar,CartesianGrid,ComposedChart,Legend,Line,XAxis,YAxis} from 'recharts';
import {ChartContainer,ChartTooltip,ChartTooltipContent} from '../../components/ui/chart';
import type {CardPricePointResponseDto} from '../../dto/auctionDto';
import {formatKoreanMonthDay} from '../../utils/dateTime';

type Props={
  history:CardPricePointResponseDto[];
};

const dateLabel=(value:string)=>formatKoreanMonthDay(value);

export default function PriceChangeAreaChart({history}:Props){
  const data=useMemo(()=>history
    .map(point=>({
      date:point.date,
      timestamp:new Date(point.date).getTime(),
      averagePrice:point.average_price??0,
      noTrade:point.average_price===null,
      auctionCount:point.ended_auction_count,
    }))
    .filter(point=>Number.isFinite(point.timestamp))
    .sort((a,b)=>a.timestamp-b.timestamp)
    .filter((point,index,points)=>points[index+1]?.timestamp!==point.timestamp),
  [history]);
  const config={
    averagePrice:{label:'일자별 평균 낙찰가',color:'#16ad64'},
    auctionCount:{label:'일자별 총 낙찰 수',color:'#e2e2e2'},
  };
  const tickInterval=Math.max(Math.ceil(data.length/5)-1,0);

  return <div
    className="detail-market-chart-wrap"
    onMouseDown={event=>event.preventDefault()}
  >
    <ChartContainer config={config} className="detail-market-chart">
      <ComposedChart data={data} margin={{top:14,right:8,bottom:2,left:8}}>
        <defs>
          <linearGradient id="detail-price-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="var(--color-averagePrice)" stopOpacity={0.3}/>
            <stop offset="95%" stopColor="var(--color-averagePrice)" stopOpacity={0.02}/>
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke="#e8ecea" strokeDasharray="3 3"/>
        <XAxis
          dataKey="date"
          interval={tickInterval}
          axisLine={false}
          tickLine={false}
          tick={{fill:'#8b928e',fontSize:10}}
          tickFormatter={dateLabel}
        />
        <YAxis
          yAxisId="price"
          dataKey="averagePrice"
          domain={['auto','auto']}
          axisLine={false}
          tickLine={false}
          tick={{fill:'#8b928e',fontSize:10}}
          tickFormatter={value=>`${Math.round(Number(value)/10000)}만`}
          width={42}
        />
        <YAxis
          yAxisId="bids"
          orientation="right"
          allowDecimals={false}
          axisLine={false}
          tickLine={false}
          tick={{fill:'#8b928e',fontSize:10}}
          tickFormatter={value=>`${Math.round(Number(value))}건`}
          width={42}
        />
        <ChartTooltip
          cursor={{stroke:'#cfd5d1',strokeDasharray:'3 3'}}
          content={<ChartTooltipContent labelFormatter={dateLabel}/>}
        />
        <Legend
          verticalAlign="bottom"
          content={()=><div className="home-chart-legend detail-chart-legend">
            <span><i className="price-line"/>일자별 평균 낙찰가</span>
            <span><i className="bid-square"/>일자별 총 낙찰 수</span>
          </div>}
        />
        <Bar
          yAxisId="bids"
          dataKey="auctionCount"
          fill="var(--color-auctionCount)"
          radius={[4,4,0,0]}
          maxBarSize={10}
        />
        <Area
          yAxisId="price"
          type="monotone"
          dataKey="averagePrice"
          stroke="none"
          fill="url(#detail-price-fill)"
          dot={false}
          activeDot={false}
        />
        <Line
          yAxisId="price"
          type="monotone"
          dataKey="averagePrice"
          stroke="var(--color-averagePrice)"
          strokeWidth={2.5}
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
          dot={false}
          activeDot={false}
        />
      </ComposedChart>
    </ChartContainer>
    {!data.length&&<div className="chart-empty">최근 30일 시세 데이터가 없습니다.</div>}
  </div>;
}
