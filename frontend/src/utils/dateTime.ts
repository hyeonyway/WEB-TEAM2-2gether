import {locale} from '../i18n';

const LOCALE=locale==='ko'?'ko-KR':'en-US';

export const localizedDateFormatter=new Intl.DateTimeFormat(LOCALE,{
  timeZone:'UTC',
  year:'numeric',
  month:'2-digit',
  day:'2-digit',
});

export const localizedMonthDayFormatter=new Intl.DateTimeFormat(LOCALE,{
  timeZone:'UTC',
  month:'2-digit',
  day:'2-digit',
});

const koreanDateFormatter=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'});
const koreanMonthDayFormatter=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'2-digit',day:'2-digit'});

export function formatLocalizedDate(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):localizedDateFormatter.format(date);
}

export function formatLocalizedMonthDay(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):localizedMonthDayFormatter.format(date)
    .replaceAll('.','/')
    .replaceAll(' ','')
    .replace(/\/$/,'');
}

export function formatKoreanDate(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):koreanDateFormatter.format(date);
}
export function formatKoreanMonthDay(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):koreanMonthDayFormatter.format(date).replaceAll('.','/').replaceAll(' ','').replace(/\/$/,'');
}
