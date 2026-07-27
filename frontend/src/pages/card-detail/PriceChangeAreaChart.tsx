import {useMemo} from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type {CardPricePointResponseDto} from '../../dto/auctionDto';

type Props={
  history:CardPricePointResponseDto[];
};

const money=(value:number)=>`${value.toLocaleString()}원`;
const dateLabel=(value:number)=>new Date(value).toLocaleDateString('ko-KR',{month:'2-digit',day:'2-digit'}).replaceAll('.','/').replaceAll(' ','').replace(/\/$/,'');

export default function PriceChangeAreaChart({history}:Props){
  const chart=useMemo(()=>{
    const end=new Date();
    end.setHours(23,59,59,999);
    const start=new Date(end);
    start.setDate(start.getDate()-29);
    start.setHours(0,0,0,0);
    const data=history
      .map(point=>({
      timestamp:new Date(point.date).getTime(),
      price:point.average_price,
      changeRate:point.change_rate,
      }))
      .filter(point=>point.timestamp>=start.getTime()&&point.timestamp<=end.getTime())
      .sort((a,b)=>a.timestamp-b.timestamp);
    const day=24*60*60*1000;
    return {
      data,
      start:start.getTime(),
      end:end.getTime(),
      ticks:[start.getTime(),start.getTime()+7*day,start.getTime()+14*day,start.getTime()+21*day,end.getTime()],
    };
  },[history]);

  return <div className="shadcn-area-chart" onMouseDown={event=>event.preventDefault()}>
    <div className="shadcn-chart-canvas">
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart
        data={chart.data}
        margin={{top:12,right:12,left:-8,bottom:0}}
        accessibilityLayer={false}
      >
        <defs>
          <linearGradient id="price-change-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#16ad64" stopOpacity={0.32}/>
            <stop offset="95%" stopColor="#16ad64" stopOpacity={0.02}/>
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke="#e8ecea" strokeDasharray="3 3"/>
        <XAxis
          dataKey="timestamp"
          type="number"
          scale="time"
          domain={[chart.start,chart.end]}
          ticks={chart.ticks}
          allowDataOverflow
          axisLine={false}
          tickLine={false}
          tick={{fill:'#8b928e',fontSize:10}}
          tickFormatter={dateLabel}
        />
        <YAxis
          axisLine={false}
          tickLine={false}
          tick={{fill:'#8b928e',fontSize:10}}
          tickFormatter={value=>`${Number(value)>0?'+':''}${value}%`}
          width={48}
        />
        <Tooltip
          cursor={{stroke:'#16ad64',strokeDasharray:'3 3'}}
          content={({active,payload})=>{
            const point=payload?.[0]?.payload as typeof chart.data[number]|undefined;
            if(!active||!point)return null;
            return <div className="chart-tooltip">
              <span>{dateLabel(point.timestamp)}</span>
              <strong>{point.changeRate>=0?'+':''}{point.changeRate.toFixed(2)}%</strong>
              <small>평균 {money(point.price)}</small>
            </div>;
          }}
        />
        <Area
          type="monotone"
          dataKey="changeRate"
          stroke="#16ad64"
          strokeWidth={2.5}
          fill="url(#price-change-fill)"
          activeDot={{r:5,fill:'#fff',stroke:'#16ad64',strokeWidth:3}}
          dot={false}
        />
      </AreaChart>
    </ResponsiveContainer>
    {!chart.data.length&&<div className="chart-empty">최근 30일 시세 데이터가 없습니다.</div>}
    </div>
  </div>;
}
