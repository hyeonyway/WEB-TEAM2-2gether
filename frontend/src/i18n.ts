export type Locale='ko'|'en';

const messages:Record<Locale,Record<string,string>>={
  ko:{justNow:'방금 전',minute:'분 전',hour:'시간 전',day:'일 전',auctionEnded:'경매 종료'},
  en:{justNow:'just now',minute:'m ago',hour:'h ago',day:'d ago',auctionEnded:'Auction ended'},
};

// 한국어를 기본값으로 유지하고, 운영 환경에서는 저장된 사용자 선택으로 전환한다.
// 영어 화면은 localStorage.setItem('dbidding-locale', 'en')으로 선택할 수 있다.
const savedLocale=typeof localStorage==='undefined'?null:localStorage.getItem('dbidding-locale');
export const locale:Locale=savedLocale==='en'?'en':'ko';
export const t=(key:string)=>messages[locale][key]??messages.en[key]??key;
