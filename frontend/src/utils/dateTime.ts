const KOREA_TIME_ZONE='Asia/Seoul';

export const koreanDateFormatter=new Intl.DateTimeFormat('ko-KR',{
  timeZone:KOREA_TIME_ZONE,
  year:'numeric',
  month:'2-digit',
  day:'2-digit',
});

export const koreanMonthDayFormatter=new Intl.DateTimeFormat('ko-KR',{
  timeZone:KOREA_TIME_ZONE,
  month:'2-digit',
  day:'2-digit',
});

export function formatKoreanDate(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):koreanDateFormatter.format(date);
}

export function formatKoreanMonthDay(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):koreanMonthDayFormatter.format(date)
    .replaceAll('.','/')
    .replaceAll(' ','')
    .replace(/\/$/,'');
}
