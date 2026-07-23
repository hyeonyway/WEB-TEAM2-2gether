import type {AuctionPayload,CardRecognition,SellPhoto,UploadedPhoto} from '../dto/sellDto';
import mockupData from '../mocks/mockup-data.json';
import {request} from './httpClient';

const wait=(ms:number)=>new Promise(resolve=>setTimeout(resolve,ms));
const useMock=import.meta.env.VITE_USE_MOCK_API==='true';

export async function scanCardImage(file:File):Promise<CardRecognition>{
  if(useMock){await wait(700);return mockupData.card_recognition as CardRecognition}
  const body=new FormData();body.append('image',file);
  const response=await fetch(`${import.meta.env.VITE_API_BASE_URL??''}/api/cards/ocr`,{method:'POST',body});
  if(!response.ok)throw new Error('OCR 요청에 실패했습니다.');
  return response.json() as Promise<CardRecognition>;
}

export async function lookupPsaCertification(certificationNumber:string):Promise<CardRecognition>{
  if(useMock){await wait(700);return {...mockupData.card_recognition,...mockupData.psa_certification} as CardRecognition}
  return request<CardRecognition>(`/api/psa-certifications/${certificationNumber}`);
}

export async function uploadSellImages(photos:SellPhoto[]):Promise<UploadedPhoto[]>{
  if(useMock){await wait(450);return photos.map((photo,order)=>({order,url:photo.url}))}
  const body=new FormData();
  photos.forEach(photo=>body.append('images',photo.file));
  const response=await fetch(`${import.meta.env.VITE_API_BASE_URL??''}/api/uploads`,{method:'POST',body});
  if(!response.ok)throw new Error('이미지 업로드에 실패했습니다.');
  return response.json() as Promise<UploadedPhoto[]>;
}

export async function createAuction(payload:AuctionPayload){
  if(useMock){await wait(450);return {id:1,...payload}}
  return request<{id:number}>('/api/auctions',{method:'POST',body:JSON.stringify(payload)});
}
