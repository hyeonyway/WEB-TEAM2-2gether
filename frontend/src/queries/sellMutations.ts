import {mutationOptions} from '@tanstack/react-query';
import {fetchCardDetail,fetchCardPage} from '../api/auctionApi';
import {createAuction,uploadSellImages} from '../api/sellApi';
import type {CardDto} from '../dto/auctionDto';
import type {AuctionPayload,RegisterAuctionRequestDto,SellForm} from '../dto/sellDto';
import {auctionQueryKeys} from './auctionQueries';

const normalized=(value:string)=>value.trim().toLocaleLowerCase().replace(/\s+/g,' ');
const normalizedGrade=(value:string|null|undefined)=>normalized(value??'').replace(/^psa\s*/,'');
const normalizedLanguage=(value:string)=>{
  const language=normalized(value);
  if(['kr','korean','한국어'].includes(language))return 'KR';
  if(['en','english','영어'].includes(language))return 'EN';
  if(['jp','japanese','일본어'].includes(language))return 'JP';
  return language;
};

async function resolveCard(form:SellForm){
  const selectedGrade=form.gradeType==='psa'?form.psaGrade:form.selfGrade;
  const cards:CardDto[]=[];
  let page=0;
  let hasNext=true;
  while(hasNext){
    const response=await fetchCardPage({keyword:form.cardName,psaGrade:null},page,100);
    cards.push(...response.content.filter(card=>normalized(card.name)===normalized(form.cardName)));
    hasNext=response.has_next;
    page++;
  }
  const details=await Promise.all(cards.map(card=>fetchCardDetail(card.id)));
  const matches=details.filter(card=>
    normalized(card.name)===normalized(form.cardName)
    &&(!form.setName.trim()||normalized(card.set_name)===normalized(form.setName))
    && normalizedLanguage(card.language)===normalizedLanguage(form.language)
    && normalizedGrade(card.psa_grade)===normalizedGrade(selectedGrade)
  );
  if(matches.length===0)throw new Error('입력한 세트, 언어, 등급과 일치하는 카드 정보를 찾을 수 없습니다.');
  if(matches.length>1)throw new Error('카드 정보가 여러 건입니다. 세트, 언어, 등급을 더 정확히 입력해 주세요.');
  return matches[0];
}

const fingerprint=(request:RegisterAuctionRequestDto)=>JSON.stringify({
  form:request.form,
  photos:request.photos.map(photo=>({
    id:photo.id,
    name:photo.file.name,
    size:photo.file.size,
    type:photo.file.type,
    lastModified:photo.file.lastModified,
  })),
  psaCertification:request.psaCertification,
});

export function createRegistrationSubmission(){
  let currentFingerprint:string|undefined;
  let idempotencyKey:string|undefined;
  let prepared:Promise<AuctionPayload>|undefined;

  return {
    async prepare(request:RegisterAuctionRequestDto,loader:()=>Promise<AuctionPayload>){
      const nextFingerprint=fingerprint(request);
      if(currentFingerprint!==nextFingerprint){
        currentFingerprint=nextFingerprint;
        idempotencyKey=crypto.randomUUID();
        prepared=undefined;
      }
      if(!prepared){
        prepared=loader().catch(error=>{
          prepared=undefined;
          throw error;
        });
      }
      return {payload:await prepared,idempotencyKey:idempotencyKey!};
    },
    clear(){
      currentFingerprint=undefined;
      idempotencyKey=undefined;
      prepared=undefined;
    },
  };
}

export const sellMutations={
  register:(submission=createRegistrationSubmission())=>mutationOptions({
    mutationKey:['sell','register'],
    mutationFn:async(request:RegisterAuctionRequestDto)=>{
      const prepared=await submission.prepare(request,async()=>{
        const card=request.itemId===undefined
          ?await resolveCard(request.form)
          :{id:request.itemId};
        const photos=await uploadSellImages(request.photos);
        return {
          itemId:card.id,
          form:request.form,
          photos,
          psaCertification:request.psaCertification,
        };
      });
      return createAuction(prepared.payload,prepared.idempotencyKey);
    },
    onSuccess:(_data,_variables,_onMutateResult,context)=>{
      submission.clear();
      return context.client.invalidateQueries({queryKey:auctionQueryKeys.lists()});
    },
  }),
};
