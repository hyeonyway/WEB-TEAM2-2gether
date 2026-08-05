export type GradeType='self'|'psa';

export type SellForm={
  cardName:string;
  setName:string;
  year:string;
  cardNumber:string;
  language:string;
  gradeType:GradeType;
  psaGrade:string;
  population:string;
  selfGrade:string;
  description:string;
  sellerMemo:string;
  startPrice:string;
  bidIncrement:string;
  buyNowEnabled:boolean;
  buyNowPrice:string;
  duration:string;
  shipping:string;
};

export type SellPhoto={
  id:string;
  file:File;
  url:string;
};

export type UploadedPhoto={
  order:number;
  uploadToken:string;
};

export type CardRecognition=Partial<SellForm>;

export type AuctionPayload={
  itemId:number;
  form:SellForm;
  photos:UploadedPhoto[];
  psaCertification:string|null;
};

export type RegisterAuctionRequestDto={
  itemId?:number;
  form:SellForm;
  photos:SellPhoto[];
  psaCertification:string|null;
};
