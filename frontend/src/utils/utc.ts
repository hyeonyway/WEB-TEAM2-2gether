export const nowUtc=()=>Date.now();

export function parseUtc(value:string|number|Date):number{
  const timestamp=value instanceof Date?value.getTime():typeof value==='number'?value:Date.parse(value);
  return Number.isNaN(timestamp)?NaN:timestamp;
}

export function utcDateParts(date=new Date()):{year:number;month:number;day:number}{
  return {year:date.getUTCFullYear(),month:date.getUTCMonth()+1,day:date.getUTCDate()};
}
