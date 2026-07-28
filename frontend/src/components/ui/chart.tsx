import {createContext,useContext,useId} from 'react';
import type {CSSProperties,ReactNode} from 'react';
import {ResponsiveContainer,Tooltip} from 'recharts';

export type ChartConfig=Record<string,{
  label:string;
  color:string;
}>;

const ChartContext=createContext<ChartConfig>({});

export function ChartContainer({
  config,
  className='',
  children,
}:{
  config:ChartConfig;
  className?:string;
  children:ReactNode;
}){
  const id=useId().replaceAll(':','');
  const colors=Object.fromEntries(
    Object.entries(config).map(([key,item])=>[`--color-${key}`,item.color]),
  ) as CSSProperties;

  return <ChartContext.Provider value={config}>
    <div data-chart={id} className={`shadcn-chart ${className}`} style={colors}>
      <ResponsiveContainer width="100%" height="100%">
        {children as never}
      </ResponsiveContainer>
    </div>
  </ChartContext.Provider>;
}

export const ChartTooltip=Tooltip;

export function ChartTooltipContent({
  active,
  payload,
  label,
  labelFormatter,
}:{
  active?:boolean;
  payload?:Array<{dataKey?:string|number;value?:number;color?:string}>;
  label?:string;
  labelFormatter?:(label:string)=>string;
}){
  const config=useContext(ChartContext);
  if(!active||!payload?.length)return null;

  return <div className="shadcn-chart-tooltip">
    <strong>{labelFormatter?labelFormatter(String(label)):label}</strong>
    {payload.map(item=>{
      const key=String(item.dataKey);
      const definition=config[key];
      return <div key={key}>
        <i style={{background:item.color??definition?.color}}/>
        <span>{definition?.label??key}</span>
        <b>{key==='averagePrice'
          ?`${Number(item.value).toLocaleString()}원`
          :`${Number(item.value).toLocaleString()}건`}</b>
      </div>;
    })}
  </div>;
}
