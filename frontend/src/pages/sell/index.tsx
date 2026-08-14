import React,{useEffect,useRef,useState}from'react';
import{useMutation}from'@tanstack/react-query';
import{CheckCircle2,Info,Search,X}from'lucide-react';
import{useNavigate}from'react-router-dom';
import{SellPageHeader,SellProgress,SellStepActions}from'./components';
import{fetchPsaCertificationSample,lookupPsaCertification}from'../../api/sellApi';
import{HttpError}from'../../api/httpClient';
import{fetchCardDetail,fetchCardPage}from'../../api/auctionApi';
import type{CardDetailResponseDto,CardDto}from'../../dto/auctionDto';
import type{SellPhoto}from'../../dto/sellDto';
import{createRegistrationSubmission,sellMutations}from'../../queries/sellMutations';
import{initialSellForm}from'./data/initialState';

const digits=value=>value.replace(/\D/g,'');
const money=value=>value?Number(value).toLocaleString():'';
const formLanguage=language=>({JP:'일본어',Japanese:'일본어',EN:'영어',English:'영어',KR:'한국어',Korean:'한국어'}[language]??'기타');
const psaValue=grade=>grade.replace(/^PSA\s*/i,'');
const isPsaGrade=grade=>/^PSA\s*/i.test(grade??'')||/^(10|[1-9])$/.test((grade??'').trim());
const isNotFoundPsaError=(error:unknown)=>error instanceof HttpError
  ?error.status===404
  :typeof error==='object'&&error!==null&&'status' in error&&(error as {status?:unknown}).status===404;
type SelectedCard=Pick<CardDetailResponseDto,'id'|'name'|'set_name'|'psa_grade'|'language'>;
type CardCandidate={key:string;name:string;setName:string;language:CardDto['language'];imageUrl:string|null;variants:CardDto[]};

const groupCards=(cards:CardDto[]):CardCandidate[]=>{
  const groups=new Map<string,CardCandidate>();
  for(const card of cards){
    const setName=card.setName??'';
    const key=[card.name,setName,card.language,card.imageUrl??''].join('|');
    const group=groups.get(key);
    if(group)group.variants.push(card);
    else groups.set(key,{key,name:card.name,setName,language:card.language,imageUrl:card.imageUrl,variants:[card]});
  }
  return [...groups.values()];
};

const initialVariant=(variants:CardDto[],selfGrade:string)=>
  variants.find(card=>!isPsaGrade(card.psaGrade)&&card.psaGrade===selfGrade)
  ??variants.find(card=>!isPsaGrade(card.psaGrade))
  ??variants[0];

export default function SellPage({Header}){
  const navigate=useNavigate();
  const[step,setStep]=useState(1);
  const[form,setForm]=useState(initialSellForm);
  const[panel,setPanel]=useState(null);
  const[psaNumber,setPsaNumber]=useState('');
  const[psaStatus,setPsaStatus]=useState('idle');
  const[samplePsaError,setSamplePsaError]=useState('');
  const[photos,setPhotos]=useState<SellPhoto[]>([]);
  const[photoError,setPhotoError]=useState('');
  const[submitStatus,setSubmitStatus]=useState('idle');
  const[submitError,setSubmitError]=useState('');
  const[selectedCard,setSelectedCard]=useState<SelectedCard|null>(null);
  const[cardVariants,setCardVariants]=useState<CardDto[]>([]);
  const[cardResults,setCardResults]=useState<CardDto[]>([]);
  const[cardSearchStatus,setCardSearchStatus]=useState('idle');
  const[psaMismatch,setPsaMismatch]=useState(false);
  const[cardSelectionError,setCardSelectionError]=useState('');
  const registrationSubmission=useRef(createRegistrationSubmission()).current;
  const submittingRef=useRef(false);
  const cardNameInputRef=useRef<HTMLInputElement>(null);
  const photosRef=useRef<SellPhoto[]>([]);
  const selectedCardIdRef=useRef<number|null>(null);
  const psaNumberRef=useRef('');
  const psaLookupSequence=useRef(0);
  const registerAuction=useMutation(sellMutations.register(registrationSubmission));
  const setField=(key,value)=>setForm(current=>({...current,[key]:value}));
  const validYear=!form.year||/^\d{4}$/.test(form.year);
  const validPopulation=!form.population||/^\d+$/.test(form.population);
  const validPsa=/^\d{7,10}$/.test(psaNumber);
  const validDuration=Number(form.duration)>0&&Number(form.duration)<=24;
  const psaVerified=form.gradeType!=='psa'||psaStatus==='success';
  const valid=[false,Boolean(selectedCard&&form.cardName.trim()&&validYear&&validPopulation&&psaVerified),Boolean(photos.length&&form.description.trim()),Boolean(Number(form.startPrice)>0&&Number(form.bidIncrement)>0&&Number(form.shipping)>=0&&validDuration&&(!form.buyNowEnabled||Number(form.buyNowPrice)>Number(form.startPrice))),true];

  useEffect(()=>{
    photosRef.current=photos;
  },[photos]);

  useEffect(()=>()=>{
    photosRef.current.forEach(photo=>URL.revokeObjectURL(photo.url));
  },[]);

  useEffect(()=>{
    const keyword=form.cardName.trim();
    if(selectedCard?.name===keyword||keyword.length<2){setCardResults([]);setCardSearchStatus('idle');return}
    let cancelled=false;
    const timer=window.setTimeout(()=>{
      setCardSearchStatus('loading');
      void fetchCardPage({keyword,psaGrade:null,sort:'NAME'},0,100)
        .then(response=>{if(!cancelled){setCardResults(response.content);setCardSearchStatus('success')}})
        .catch(()=>{if(!cancelled){setCardResults([]);setCardSearchStatus('error')}});
    },250);
    return()=>{cancelled=true;window.clearTimeout(timer)};
  },[form.cardName,selectedCard]);

  const clearSelectedCard=()=>{selectedCardIdRef.current=null;setSelectedCard(null);setCardVariants([]);setPsaMismatch(false);setCardSelectionError('')};
  const resetCardSelection=()=>{
    clearSelectedCard();
    setCardResults([]);
    setCardSearchStatus('idle');
    setField('cardName','');
    window.setTimeout(()=>cardNameInputRef.current?.focus());
  };
  const resetPsaVerification=()=>{
    if(form.gradeType==='psa')clearSelectedCard();
    setForm(current=>({...current,gradeType:'self',psaGrade:'',population:''}));
    setPsaStatus('idle');
  };
  const lookupPsa=async()=>{
    if(!validPsa)return;
    const requestedNumber=psaNumber;
    const sequence=++psaLookupSequence.current;
    setPsaStatus('loading');
    let certification;
    try{
      certification=await lookupPsaCertification(requestedNumber);
    }catch(error){
      if(sequence!==psaLookupSequence.current||psaNumberRef.current!==requestedNumber)return;
      setPsaStatus(isNotFoundPsaError(error)?'not-found':'error');
      return;
    }
    try{
      const detail=await fetchCardDetail(certification.itemId);
      if(sequence!==psaLookupSequence.current||psaNumberRef.current!==requestedNumber)return;
      applyPsaCardSelection(detail,certification);
      setPsaStatus('success');
    }catch{
      if(sequence!==psaLookupSequence.current||psaNumberRef.current!==requestedNumber)return;
      setPsaStatus('card-error');
    }
  };
  const fillSamplePsa=async()=>{
    setSamplePsaError('');
    try{
      const sample=await fetchPsaCertificationSample();
      psaLookupSequence.current++;
      psaNumberRef.current=sample.certificationNumber;
      setPsaNumber(sample.certificationNumber);
      resetPsaVerification();
    }catch{
      setSamplePsaError('예시 인증번호를 불러오지 못했습니다. 다시 시도해 주세요.');
    }
  };
  const applyCardSelection=(card:CardDto,variants:CardDto[])=>{
    const grade=card.psaGrade;
    setCardVariants(variants);
    setCardResults([]);
    setCardSearchStatus('idle');
    setPsaMismatch(false);
    setCardSelectionError('');
    selectedCardIdRef.current=card.id;
    setSelectedCard({id:card.id,name:card.name,set_name:card.setName??'세트 정보 확인 중',psa_grade:grade,language:card.language});
    setForm(current=>({...current,
      cardName:card.name,
      language:formLanguage(card.language),
      gradeType:'self',
      psaGrade:'',
      selfGrade:grade,
    }));
    void fetchCardDetail(card.id).then(detail=>{
      setSelectedCard(current=>current?.id===card.id?detail:current);
      setForm(current=>selectedCardIdRef.current===card.id?{...current,setName:detail.set_name}:current);
    }).catch(()=>{});
  };
  const applyPsaCardSelection=(detail:CardDetailResponseDto,certification:{psaGrade:string;population:string;issuedYear:string;cardNumber:string})=>{
    setCardVariants([]);
    setCardResults([]);
    setCardSearchStatus('idle');
    setPsaMismatch(false);
    selectedCardIdRef.current=detail.id;
    setSelectedCard(detail);
    setForm(current=>({...current,
      cardName:detail.name,
      setName:detail.set_name,
      language:formLanguage(detail.language),
      gradeType:'psa',
      psaGrade:certification.psaGrade,
      population:certification.population,
      year:certification.issuedYear,
      cardNumber:certification.cardNumber,
    }));
  };
  const selectCard=(candidate:CardCandidate)=>{
    const variant=form.gradeType==='psa'&&form.psaGrade
      ?candidate.variants.find(card=>isPsaGrade(card.psaGrade)&&psaValue(card.psaGrade)===form.psaGrade)
      :undefined;
    if(form.gradeType==='psa'&&form.psaGrade&&!variant){
      setPsaMismatch(true);
      return;
    }
    const selected=variant??initialVariant(candidate.variants,form.selfGrade);
    if(isPsaGrade(selected.psaGrade)){
      setCardSelectionError('PSA 등급 카드는 인증번호 조회로만 선택할 수 있습니다.');
      return;
    }
    applyCardSelection(selected,candidate.variants);
  };
  const selectSelfGrade=(grade:string)=>{
    const variant=cardVariants.find(card=>card.psaGrade===grade);
    if(!variant)return;
    applyCardSelection(variant,cardVariants);
  };
  const addPhotos=event=>{const selected=[...(event.target.files||[])],accepted=selected.filter(file=>['image/png','image/jpeg'].includes(file.type)&&file.size<=10*1024*1024),room=8-photos.length;if(selected.length>room)setPhotoError('사진은 최대 8장까지 등록할 수 있습니다.');else if(accepted.length!==selected.length)setPhotoError('PNG, JPG 형식의 10MB 이하 이미지만 등록할 수 있습니다.');else setPhotoError('');setPhotos(current=>[...current,...accepted.slice(0,room).map(file=>({id:crypto.randomUUID(),file,url:URL.createObjectURL(file)}))]);event.target.value=''};
  const removePhoto=id=>setPhotos(current=>{const target=current.find(item=>item.id===id);if(target)URL.revokeObjectURL(target.url);return current.filter(item=>item.id!==id)});
  const submit=async()=>{
    if(submittingRef.current)return;
    if(!valid[1]||!valid[2]||!valid[3]){
      setSubmitError('입력 내용을 다시 확인해 주세요.');
      return;
    }
    submittingRef.current=true;
    setSubmitStatus('loading');
    setSubmitError('');
    try{
      const auction=await registerAuction.mutateAsync({itemId:selectedCard?.id,form,photos,psaCertification:psaStatus==='success'?psaNumber:null});
      setSubmitStatus('success');
      setTimeout(()=>navigate('/auction/'+auction.id),500);
    }catch{
      setSubmitStatus('error');
      setSubmitError('등록에 실패했습니다. 입력값은 유지되었습니다. 잠시 후 다시 시도해 주세요.');
    }finally{
      submittingRef.current=false;
    }
  };
  const review=[['카드명',form.cardName],['세트명',form.setName||'-'],['발행 연도',form.year||'-'],['카드 번호',form.cardNumber||'-'],['언어',form.language],['등급',form.gradeType==='psa'?'PSA '+form.psaGrade:form.selfGrade],['시작가',money(form.startPrice)+'원'],['즉시 구매가',form.buyNowEnabled?money(form.buyNowPrice)+'원':'없음'],['호가 단위',money(form.bidIncrement)+'원'],['경매 시간',form.duration+'시간'],['배송비',money(form.shipping||'0')+'원'],['등록 사진',photos.length+'장']];

  return <div className="sell-page">{Header&&<Header/>}<main><SellPageHeader/>
    <SellProgress step={step}/>
    <section className="sell-step-card">
      {step===1&&<StepOne form={form} setField={setField} panel={panel} setPanel={setPanel} psaNumber={psaNumber} setPsaNumber={value=>{const next=digits(value);psaNumberRef.current=next;psaLookupSequence.current++;setPsaNumber(next);resetPsaVerification()}} validPsa={validPsa} lookupPsa={lookupPsa} fillSamplePsa={fillSamplePsa} psaStatus={psaStatus} samplePsaError={samplePsaError} validYear={validYear} validPopulation={validPopulation} selectedCard={selectedCard} psaMismatch={psaMismatch} cardSelectionError={cardSelectionError} cardResults={groupCards(cardResults)} cardSearchStatus={cardSearchStatus} clearSelectedCard={clearSelectedCard} resetCardSelection={resetCardSelection} selectCard={selectCard} selectSelfGrade={selectSelfGrade} psaVerified={psaVerified} cardNameInputRef={cardNameInputRef}/>}
      {step===2&&<StepTwo form={form} setField={setField} photos={photos} addPhotos={addPhotos} removePhoto={removePhoto} photoError={photoError}/>}
      {step===3&&<StepThree form={form} setField={setField}/>}
      {step===4&&<Review form={form} photos={photos} psaStatus={psaStatus} psaNumber={psaNumber} review={review} submitStatus={submitStatus} submitError={submitError}/>}
      <SellStepActions step={step} canContinue={valid[step]} submitStatus={submitStatus} onPrevious={()=>setStep(value=>value-1)} onNext={()=>setStep(value=>value+1)} onSubmit={submit}/>
    </section>
  </main></div>;
}

function Title({step,title,copy}){return <div className="sell-step-title"><small>STEP {step}</small><h2>{title}</h2><p>{copy}</p></div>}
function ErrorText({children}){return <p className="form-error" role="alert">{children}</p>}

function StepOne({form,setField,panel,setPanel,psaNumber,setPsaNumber,validPsa,lookupPsa,fillSamplePsa,psaStatus,samplePsaError,validYear,validPopulation,selectedCard,psaMismatch,cardSelectionError,cardResults,cardSearchStatus,clearSelectedCard,resetCardSelection,selectCard,selectSelfGrade,psaVerified,cardNameInputRef}){
  return <><Title step="1" title="카드 정보" copy="직접 입력하거나 PSA 인증으로 카드 정보를 자동 완성하세요."/><div className="sell-assist-buttons sell-single-action"><button type="button" className={panel==='psa'?'active':''} onClick={()=>setPanel(panel==='psa'?null:'psa')}>PSA 인증 조회</button></div>
  {panel==='psa'&&<div className="sell-assist-panel"><h3>PSA 인증 조회</h3><button type="button" onClick={fillSamplePsa}>예시 인증번호 채우기</button><label htmlFor="psa-number">PSA 인증번호</label><div className="sell-inline-control"><input id="psa-number" inputMode="numeric" maxLength={10} value={psaNumber} onChange={e=>setPsaNumber(e.target.value)} placeholder="7~10자리 인증번호" aria-describedby="psa-help"/><button type="button" disabled={!validPsa||psaStatus==='loading'} onClick={lookupPsa}>{psaStatus==='loading'?'조회 중…':'조회'}</button></div><small id="psa-help">숫자 7~10자리 인증번호를 입력해 주세요.</small>{samplePsaError&&<ErrorText>{samplePsaError}</ErrorText>}{psaNumber&&!validPsa&&<ErrorText>인증번호는 숫자 7~10자리여야 합니다.</ErrorText>}{psaStatus==='not-found'&&<ErrorText>등록된 PSA 번호가 아닙니다.</ErrorText>}{psaStatus==='card-error'&&<ErrorText>카드 정보를 불러오지 못했습니다. 다시 시도해 주세요.</ErrorText>}{psaStatus==='error'&&<ErrorText>PSA 인증 조회에 실패했습니다. 다시 시도해 주세요.</ErrorText>}{psaStatus==='success'&&<p className="form-success"><CheckCircle2/>PSA {psaNumber} 인증이 완료되었습니다.</p>}</div>}
  <div className="sell-field-list"><label htmlFor="card-name">카드명 <em>필수</em></label><div className="sell-card-search"><Search/><input ref={cardNameInputRef} id="card-name" value={form.cardName} onChange={e=>{clearSelectedCard();setField('cardName',e.target.value)}} autoComplete="off" placeholder="카드명을 입력해 선택하세요" required/>{form.cardName.trim()&&<button type="button" onClick={resetCardSelection} aria-label="입력한 카드명 지우기"><X/></button>}</div>{selectedCard?<p className="sell-card-selected"><CheckCircle2/>선택됨: {selectedCard.name} · {selectedCard.set_name} · {selectedCard.psa_grade}</p>:cardSelectionError?<ErrorText>{cardSelectionError}</ErrorText>:psaMismatch?<ErrorText>인증 등급과 일치하는 카드 정보를 다시 선택해 주세요.</ErrorText>:!psaVerified?<p className="sell-card-search-status">PSA 인증 조회 후 해당 등급의 카드를 선택할 수 있습니다.</p>:<><CardSearchResults status={cardSearchStatus} cards={cardResults} onSelect={selectCard}/>{cardSearchStatus==='success'&&cardResults.length>0&&<p className="sell-card-search-status">검색 결과에서 카드를 선택해 주세요.</p>}</>}<div className="sell-field-row"><Field id="set-name" label="세트명" value={form.setName} onChange={value=>setField('setName',value)}/><Field id="year" label="발행 연도" value={form.year} inputMode="numeric" onChange={value=>setField('year',digits(value))} error={!validYear?'연도는 숫자 4자리로 입력해 주세요.':''}/></div><div className="sell-field-row"><Field id="card-number" label="카드 번호" value={form.cardNumber} onChange={value=>setField('cardNumber',value)}/><div><label htmlFor="language">언어</label><select id="language" value={form.language} onChange={e=>setField('language',e.target.value)}>{['일본어','영어','한국어','기타'].map(value=><option key={value}>{value}</option>)}</select></div></div></div>
  <fieldset className="sell-grade-fieldset"><legend>등급 및 상태</legend>{psaStatus==='success'?<><div className="sell-segment sell-single-action"><button type="button" className="active">PSA 등급</button></div><div className="sell-field-row"><div><label htmlFor="psa-grade">PSA 등급</label><select id="psa-grade" value={form.psaGrade} disabled><option value="">PSA 인증 조회 필요</option>{Array.from({length:10},(_,i)=>10-i).map(value=><option key={value}>{value}</option>)}</select></div><Field id="population" label="Population" value={form.population} disabled onChange={()=>{}}/></div></>:<><div className="sell-segment sell-single-action"><button type="button" className="active">자체 평가</button></div><Options values={['민트','근민트','우량','양호','보통','하']} value={form.selfGrade} onChange={selectSelfGrade}/></>}</fieldset></>;
}

function CardSearchResults({status,cards,onSelect}:{status:string;cards:CardCandidate[];onSelect:(candidate:CardCandidate)=>void}){
  if(status==='loading')return <p className="sell-card-search-status">카드 검색 중...</p>;
  if(status==='error')return <ErrorText>카드 목록을 불러오지 못했습니다. 다시 입력해 주세요.</ErrorText>;
  if(status==='success'&&cards.length===0)return <p className="sell-card-search-status">일치하는 카드가 없습니다.</p>;
  if(cards.length===0)return null;
  return <ul className="sell-card-results" role="listbox" aria-label="카드 검색 결과">{cards.map(card=><li key={card.key}><button type="button" onClick={()=>onSelect(card)}><img src={card.imageUrl??undefined} alt=""/><span><b>{card.name}</b><small>{card.setName} · {card.language}</small></span></button></li>)}</ul>;
}

function Field({id,label,value,onChange,inputMode=undefined,error='',disabled=false}){return <div><label htmlFor={id}>{label}</label><input id={id} value={value} inputMode={inputMode} disabled={disabled} onChange={e=>onChange(e.target.value)} aria-invalid={Boolean(error)}/>{error&&<ErrorText>{error}</ErrorText>}</div>}
function Options({values,value,onChange,className=''}){return <div className={'sell-condition-options '+className}>{values.map(item=><button type="button" key={item} className={value===item?'active':''} onClick={()=>onChange(item)}>{className==='duration'?item+'시간':item}</button>)}</div>}

function StepTwo({form,setField,photos,addPhotos,removePhoto,photoError}){
  return <><Title step="2" title="사진 및 설명" copy="상품 상태를 확인할 수 있는 사진과 설명을 등록하세요."/><div className="sell-photo-heading"><h3>상품 사진</h3><span>{photos.length} / 8장 등록</span></div><div className="sell-photo-grid">{Array.from({length:8},(_,index)=>{const photo=photos[index];if(photo)return <figure key={photo.id}><img src={photo.url} alt={'등록 사진 '+(index+1)}/>{index===0&&<figcaption>대표 이미지</figcaption>}<button type="button" onClick={()=>removePhoto(photo.id)} aria-label={(index+1)+'번 사진 삭제'}>×</button></figure>;if(index===photos.length)return <label key="upload" className="sell-photo-slot" htmlFor="product-photos"><b>+</b><span>사진 추가</span><input id="product-photos" type="file" accept="image/png,image/jpeg" multiple onChange={addPhotos}/></label>;return <div className="sell-photo-slot empty" key={index} aria-hidden="true"/>})}</div>{photoError&&<ErrorText>{photoError}</ErrorText>}<div className="sell-field-list"><label htmlFor="description">상품 상태 및 설명 <em>필수</em></label><textarea id="description" value={form.description} onChange={e=>setField('description',e.target.value)} placeholder="카드 상태, 보관 방법, 흠집 및 특이사항" required/><label htmlFor="seller-memo">판매자 메모</label><textarea id="seller-memo" value={form.sellerMemo} onChange={e=>setField('sellerMemo',e.target.value)} placeholder="구매자에게 전달할 추가 내용"/></div></>;
}

function MoneyInput({id,value,onChange}){return <div className="sell-money-input"><input id={id} inputMode="numeric" value={money(value)} onChange={e=>onChange(digits(e.target.value))}/><span>원</span></div>}
function StepThree({form,setField}){
  const buyPriceMissing=form.buyNowEnabled&&!form.buyNowPrice;
  const buyPriceTooLow=form.buyNowEnabled&&!buyPriceMissing&&Number(form.buyNowPrice)<=Number(form.startPrice);
  const durationError=form.duration&&Number(form.duration)>24;
  return <><Title step="3" title="경매 설정" copy="가격과 시간, 배송 조건을 설정하세요."/><div className="sell-field-list"><label htmlFor="start-price">시작가 <em>필수</em></label><MoneyInput id="start-price" value={form.startPrice} onChange={value=>setField('startPrice',value)}/><label htmlFor="increment">호가 단위 <em>필수</em></label><MoneyInput id="increment" value={form.bidIncrement} onChange={value=>setField('bidIncrement',value)}/><div className="sell-quick-money">{[10000,50000,100000,500000,1000000].map(value=><button type="button" key={value} className={Number(form.bidIncrement)===value?'active':''} onClick={()=>setField('bidIncrement',String(value))}>{value.toLocaleString()}원</button>)}</div><Toggle id="buy-toggle" checked={form.buyNowEnabled} onChange={value=>{setField('buyNowEnabled',value);if(!value)setField('buyNowPrice','')}} title="즉시 구매가" copy="구매자가 즉시 구매할 가격을 설정합니다."/>{form.buyNowEnabled&&<><label htmlFor="buy-price">즉시 구매 금액 <em>필수</em></label><MoneyInput id="buy-price" value={form.buyNowPrice} onChange={value=>setField('buyNowPrice',value)}/>{buyPriceMissing&&<ErrorText>즉시 구매가를 입력해 주세요.</ErrorText>}{buyPriceTooLow&&<ErrorText>즉시 구매가는 시작가보다 커야 합니다.</ErrorText>}</>}<fieldset className="sell-grade-fieldset sell-duration-fieldset"><legend>경매 시간 <em>필수</em></legend><Options className="duration" values={['4','8','12']} value={form.duration} onChange={value=>setField('duration',value)}/><label htmlFor="duration-custom">직접 입력 <small>(최대 24시간)</small></label><div className="sell-duration-input"><input id="duration-custom" inputMode="numeric" value={form.duration} onChange={e=>setField('duration',digits(e.target.value))} aria-invalid={Boolean(durationError)}/><span>시간</span></div>{durationError&&<ErrorText>경매 시간은 최대 24시간까지 입력할 수 있습니다.</ErrorText>}</fieldset><div className="sell-shipping-field"><label htmlFor="shipping">배송비</label><MoneyInput id="shipping" value={form.shipping} onChange={value=>setField('shipping',value)}/></div></div></>;
}
function Toggle({id,checked,onChange,title,copy}){return <label className="sell-toggle-row" htmlFor={id}><span><b>{title}</b><small>{copy}</small></span><input id={id} type="checkbox" checked={checked} onChange={e=>onChange(e.target.checked)}/></label>}
function Review({form,photos,psaStatus,psaNumber,review,submitStatus,submitError}){return <><Title step="4" title="등록 내용 검토" copy="경매 등록 전 입력한 내용을 마지막으로 확인하세요."/>{psaStatus==='success'&&<div className="sell-psa-verified"><CheckCircle2/><span><b>PSA 인증 완료</b><small>인증번호 {psaNumber} · 위변조 검증 통과</small></span></div>}<dl className="sell-review-list">{review.map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><div className="sell-review-description"><h3>상품 상태 및 설명</h3><p>{form.description}</p>{form.sellerMemo&&<><h3>판매자 메모</h3><p>{form.sellerMemo}</p></>}</div><div className="sell-review-photos">{photos.map((photo,index)=><img src={photo.url} alt={'검토 사진 '+(index+1)} key={photo.id}/>)}</div>{submitError&&<div className="sell-submit-error" role="alert"><Info/><span><b>등록하지 못했습니다.</b><small>{submitError} 입력 내용을 확인하고 다시 시도해 주세요.</small></span></div>}{submitStatus==='success'&&<p className="form-success"><CheckCircle2/>경매 등록이 완료되었습니다. 상세 페이지로 이동합니다.</p>}</>}
