// 이 두 포맷은 항상 Asia/Seoul로 고정한다. 여기 들어오는 값(통계 차트 날짜 등)은
// 백엔드가 Asia/Seoul 기준으로 집계한 "영업일"을 나타내는 값이라, 뷰어의 브라우저
// 타임존과 무관하게 항상 그 날짜 그대로 보여줘야 한다(예: 날짜만 있는 값을
// new Date()로 읽으면 UTC 00:00으로 해석되므로, 브라우저 로컬 타임존으로 포맷하면
// 하루 어긋날 수 있다). "언제 일어난 일"처럼 뷰어 기준으로 보여줄 값에는 이 함수
// 대신 아래 formatLocalDate를 쓴다.
const koreanDateFormatter=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'});
const koreanMonthDayFormatter=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'2-digit',day:'2-digit'});

export function formatKoreanDate(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):koreanDateFormatter.format(date);
}
export function formatKoreanMonthDay(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):koreanMonthDayFormatter.format(date).replaceAll('.','/').replaceAll(' ','').replace(/\/$/,'');
}

// 뷰어 기준으로 "언제 일어난 일"을 보여줄 때 쓴다(브라우저 로컬 타임존을 그대로
// 따름 — timeZone 옵션을 안 준 toLocaleString/Intl.DateTimeFormat과 동일한 기준).
const localDateFormatter=new Intl.DateTimeFormat('ko-KR',{year:'numeric',month:'2-digit',day:'2-digit'});

export function formatLocalDate(value:string|Date){
  const date=value instanceof Date?value:new Date(value);
  return Number.isNaN(date.getTime())?String(value):localDateFormatter.format(date);
}
