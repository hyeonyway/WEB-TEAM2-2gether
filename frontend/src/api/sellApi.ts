import type {AuctionPayload,CardRecognition,SellPhoto,UploadedPhoto} from '../dto/sellDto';
import mockupData from '../mocks/mockup-data.json';
import {request} from './httpClient';
import {authenticatedRequest} from './authenticatedRequest';
import {isMockApiEnabled} from './mockApiConfig';

const wait=(ms:number)=>new Promise(resolve=>setTimeout(resolve,ms));
const isLocalUploadEnabled=()=>import.meta.env.VITE_LOCAL_UPLOAD==='true';

export async function scanCardImage(file:File):Promise<CardRecognition>{
  if(isMockApiEnabled()){await wait(700);return mockupData.card_recognition as CardRecognition}
  const body=new FormData();body.append('image',file);
  const response=await fetch(`${import.meta.env.VITE_API_BASE_URL??''}/api/cards/ocr`,{method:'POST',body});
  if(!response.ok)throw new Error('OCR 요청에 실패했습니다.');
  return response.json() as Promise<CardRecognition>;
}

export async function lookupPsaCertification(certificationNumber:string):Promise<CardRecognition>{
  if(isMockApiEnabled()){await wait(700);return {...mockupData.card_recognition,...mockupData.psa_certification} as CardRecognition}
  return request<CardRecognition>(`/api/psa-certifications/${certificationNumber}`);
}

export async function uploadSellImages(photos:SellPhoto[]):Promise<UploadedPhoto[]>{
  if(isMockApiEnabled()){
    await wait(450);
    return photos.map((photo,order)=>({order,uploadToken:photo.url}));
  }
  if(isLocalUploadEnabled()){
    return photos.map((photo,order)=>({order,uploadToken:`local/${photo.id}`}));
  }
  const presigned=await authenticatedRequest<{
    uploads:Array<{
      upload_url:string;
      upload_token:string;
      expires_in_seconds:number;
    }>;
  }>('/api/uploads/images/presigned-url',{
    method:'POST',
    body:JSON.stringify({
      files:photos.map(photo=>({
        fileName:photo.file.name,
        contentType:photo.file.type,
      })),
    }),
  });
  if(presigned.uploads.length!==photos.length){
    throw new Error('발급된 이미지 업로드 URL 수가 일치하지 않습니다.');
  }
  await Promise.all(presigned.uploads.map(async(upload,index)=>{
    const photo=photos[index];
    const response=await fetch(upload.upload_url,{
      method:'PUT',
      headers:{'Content-Type':photo.file.type},
      body:photo.file,
    });
    if(!response.ok)throw new Error(`${index+1}번 이미지 업로드에 실패했습니다.`);
  }));
  return presigned.uploads.map((upload,order)=>({
    order,
    uploadToken:upload.upload_token,
  }));
}

export async function createAuction(payload:AuctionPayload,idempotencyKey:string){
  if(isMockApiEnabled()){await wait(450);return {id:1,...payload}}
  const {form}=payload;
  return authenticatedRequest<{id:number}>('/api/auctions',{
    method:'POST',
    headers:{'Idempotency-Key':idempotencyKey},
    body:JSON.stringify({
      itemId:payload.itemId,
      auctionName:form.cardName.trim(),
      description:form.description.trim(),
      sellerMemo:form.sellerMemo.trim()||null,
      psaCertification:payload.psaCertification,
      gradeType:form.gradeType,
      selfGrade:form.gradeType==='self'?form.selfGrade:null,
      psaGrade:form.gradeType==='psa'?form.psaGrade:null,
      imageUploadTokens:[...payload.photos]
        .sort((left,right)=>left.order-right.order)
        .map(photo=>photo.uploadToken),
      startPrice:Number(form.startPrice),
      bidIncrement:Number(form.bidIncrement),
      buyNowPrice:form.buyNowEnabled?Number(form.buyNowPrice):null,
      durationHours:Number(form.duration),
      shippingFee:Number(form.shipping),
    }),
  });
}
