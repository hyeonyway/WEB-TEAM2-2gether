import React,{useRef,useState}from'react';
import{useMutation}from'@tanstack/react-query';
import{CheckCircle2,Info}from'lucide-react';
import{SellPageHeader,SellProgress,SellStepActions}from'./components';
import{lookupPsaCertification,scanCardImage}from'../../api/sellApi';
import type{SellPhoto}from'../../dto/sellDto';
import{createRegistrationSubmission,sellMutations}from'../../queries/sellMutations';
import{initialSellForm}from'./data/initialState';

const digits=value=>value.replace(/\D/g,'');
const money=value=>value?Number(value).toLocaleString():'';

export default function SellPage({Header}){
  const[step,setStep]=useState(1);
  const[form,setForm]=useState(initialSellForm);
  const[panel,setPanel]=useState(null);
  const[ocrFile,setOcrFile]=useState(null);
  const[ocrStatus,setOcrStatus]=useState('idle');
  const[psaNumber,setPsaNumber]=useState('');
  const[psaStatus,setPsaStatus]=useState('idle');
  const[photos,setPhotos]=useState<SellPhoto[]>([]);
  const[photoError,setPhotoError]=useState('');
  const[submitStatus,setSubmitStatus]=useState('idle');
  const[submitError,setSubmitError]=useState('');
  const registrationSubmission=useRef(createRegistrationSubmission()).current;
  const registerAuction=useMutation(sellMutations.register(registrationSubmission));
  const setField=(key,value)=>setForm(current=>({...current,[key]:value}));
  const validYear=!form.year||/^\d{4}$/.test(form.year);
  const validPopulation=!form.population||/^\d+$/.test(form.population);
  const validPsa=/^\d{8}$/.test(psaNumber);
  const validDuration=Number(form.duration)>0&&Number(form.duration)<=24;
  const valid=[false,Boolean(form.cardName.trim()&&validYear&&validPopulation),Boolean(photos.length&&form.description.trim()),Boolean(Number(form.startPrice)>0&&Number(form.bidIncrement)>0&&Number(form.shipping)>=0&&validDuration&&(!form.buyNowEnabled||Number(form.buyNowPrice)>Number(form.startPrice))),true];

  const chooseOcr=event=>{const file=event.target.files?.[0];if(!file)return;if(!['image/png','image/jpeg'].includes(file.type)||file.size>10*1024*1024){setOcrStatus('error');return}setOcrFile(file);setOcrStatus('idle')};
  const scan=async()=>{if(!ocrFile)return;setOcrStatus('loading');const recognized=await scanCardImage(ocrFile);setForm(current=>({...current,...recognized}));setOcrStatus('success')};
  const lookupPsa=async()=>{if(!validPsa)return;setPsaStatus('loading');const certification=await lookupPsaCertification(psaNumber);setForm(current=>({...current,...certification}));setPsaStatus('success')};
  const addPhotos=event=>{const selected=[...(event.target.files||[])],accepted=selected.filter(file=>['image/png','image/jpeg'].includes(file.type)&&file.size<=10*1024*1024),room=8-photos.length;if(selected.length>room)setPhotoError('사진은 최대 8장까지 등록할 수 있습니다.');else if(accepted.length!==selected.length)setPhotoError('PNG, JPG 형식의 10MB 이하 이미지만 등록할 수 있습니다.');else setPhotoError('');setPhotos(current=>[...current,...accepted.slice(0,room).map(file=>({id:crypto.randomUUID(),file,url:URL.createObjectURL(file)}))]);event.target.value=''};
  const removePhoto=id=>setPhotos(current=>{const target=current.find(item=>item.id===id);if(target)URL.revokeObjectURL(target.url);return current.filter(item=>item.id!==id)});
  const submit=async()=>{if(!valid[1]||!valid[2]||!valid[3]){setSubmitError('입력 내용을 다시 확인해 주세요.');return}setSubmitStatus('loading');setSubmitError('');try{const auction=await registerAuction.mutateAsync({form,photos,psaCertification:psaStatus==='success'?psaNumber:null});setSubmitStatus('success');setTimeout(()=>{window.location.href='/auction/'+auction.id},500)}catch{setSubmitStatus('error');setSubmitError('등록에 실패했습니다. 입력값은 유지되었습니다. 잠시 후 다시 시도해 주세요.')}};
  const review=[['카드명',form.cardName],['세트명',form.setName||'-'],['발행 연도',form.year||'-'],['카드 번호',form.cardNumber||'-'],['언어',form.language],['등급',form.gradeType==='psa'?'PSA '+form.psaGrade:form.selfGrade],['시작가',money(form.startPrice)+'원'],['즉시 구매가',form.buyNowEnabled?money(form.buyNowPrice)+'원':'없음'],['호가 단위',money(form.bidIncrement)+'원'],['경매 시간',form.duration+'시간'],['배송비',money(form.shipping||'0')+'원'],['등록 사진',photos.length+'장']];

  return <div className="sell-page">{Header&&<Header/>}<main><SellPageHeader/>
    <SellProgress step={step}/>
    <section className="sell-step-card">
      {step===1&&<StepOne form={form} setField={setField} panel={panel} setPanel={setPanel} ocrFile={ocrFile} chooseOcr={chooseOcr} scan={scan} ocrStatus={ocrStatus} psaNumber={psaNumber} setPsaNumber={value=>{setPsaNumber(digits(value));setPsaStatus('idle')}} validPsa={validPsa} lookupPsa={lookupPsa} psaStatus={psaStatus} validYear={validYear} validPopulation={validPopulation}/>}
      {step===2&&<StepTwo form={form} setField={setField} photos={photos} addPhotos={addPhotos} removePhoto={removePhoto} photoError={photoError}/>}
      {step===3&&<StepThree form={form} setField={setField}/>}
      {step===4&&<Review form={form} photos={photos} psaStatus={psaStatus} psaNumber={psaNumber} review={review} submitStatus={submitStatus} submitError={submitError}/>}
      <SellStepActions step={step} canContinue={valid[step]} submitStatus={submitStatus} onPrevious={()=>setStep(value=>value-1)} onNext={()=>setStep(value=>value+1)} onSubmit={submit}/>
    </section>
  </main></div>;
}

function Title({step,title,copy}){return <div className="sell-step-title"><small>STEP {step}</small><h2>{title}</h2><p>{copy}</p></div>}
function ErrorText({children}){return <p className="form-error" role="alert">{children}</p>}

function StepOne({form,setField,panel,setPanel,ocrFile,chooseOcr,scan,ocrStatus,psaNumber,setPsaNumber,validPsa,lookupPsa,psaStatus,validYear,validPopulation}){
  return <><Title step="1" title="카드 정보" copy="직접 입력하거나 보조 기능으로 카드 정보를 자동 완성하세요."/><div className="sell-assist-buttons"><button type="button" className={panel==='ocr'?'active':''} onClick={()=>setPanel(panel==='ocr'?null:'ocr')}>OCR 자동 입력</button><button type="button" className={panel==='psa'?'active':''} onClick={()=>setPanel(panel==='psa'?null:'psa')}>PSA 인증 조회</button></div>
  {panel==='ocr'&&<div className="sell-assist-panel"><h3>OCR 자동 입력</h3><label className="sell-file-picker" htmlFor="ocr-file">{ocrFile?ocrFile.name:'카드 이미지 선택'}<small>PNG, JPG · 최대 10MB</small></label><input id="ocr-file" className="visually-hidden" type="file" accept="image/png,image/jpeg" onChange={chooseOcr}/>{ocrFile&&<button type="button" disabled={ocrStatus==='loading'} onClick={scan}>{ocrStatus==='loading'?'스캔 중…':'스캔 시작'}</button>}{ocrStatus==='success'&&<p className="form-success"><CheckCircle2/>카드 정보를 자동 입력했습니다. 수정할 수 있습니다.</p>}{ocrStatus==='error'&&<ErrorText>PNG, JPG 형식의 10MB 이하 이미지를 선택해 주세요.</ErrorText>}</div>}
  {panel==='psa'&&<div className="sell-assist-panel"><h3>PSA 인증 조회</h3><label htmlFor="psa-number">PSA 인증번호</label><div className="sell-inline-control"><input id="psa-number" inputMode="numeric" maxLength={8} value={psaNumber} onChange={e=>setPsaNumber(e.target.value)} placeholder="8자리 인증번호" aria-describedby="psa-help"/><button type="button" disabled={!validPsa||psaStatus==='loading'} onClick={lookupPsa}>{psaStatus==='loading'?'조회 중…':'조회'}</button></div><small id="psa-help">숫자 8자리 인증번호를 입력해 주세요.</small>{psaNumber&&!validPsa&&<ErrorText>인증번호는 숫자 8자리여야 합니다.</ErrorText>}{psaStatus==='success'&&<p className="form-success"><CheckCircle2/>PSA {psaNumber} 인증이 완료되었습니다.</p>}</div>}
  <div className="sell-field-list"><label htmlFor="card-name">카드명 <em>필수</em></label><input id="card-name" value={form.cardName} onChange={e=>setField('cardName',e.target.value)} required/><div className="sell-field-row"><Field id="set-name" label="세트명" value={form.setName} onChange={value=>setField('setName',value)}/><Field id="year" label="발행 연도" value={form.year} inputMode="numeric" onChange={value=>setField('year',digits(value))} error={!validYear?'연도는 숫자 4자리로 입력해 주세요.':''}/></div><div className="sell-field-row"><Field id="card-number" label="카드 번호" value={form.cardNumber} onChange={value=>setField('cardNumber',value)}/><div><label htmlFor="language">언어</label><select id="language" value={form.language} onChange={e=>setField('language',e.target.value)}>{['일본어','영어','한국어','기타'].map(value=><option key={value}>{value}</option>)}</select></div></div></div>
  <fieldset className="sell-grade-fieldset"><legend>등급 및 상태</legend><div className="sell-segment"><button type="button" className={form.gradeType==='self'?'active':''} onClick={()=>setField('gradeType','self')}>자체 평가</button><button type="button" className={form.gradeType==='psa'?'active':''} onClick={()=>setField('gradeType','psa')}>PSA 등급</button></div>{form.gradeType==='psa'?<div className="sell-field-row"><div><label htmlFor="psa-grade">PSA 등급</label><select id="psa-grade" value={form.psaGrade} disabled><option value="">PSA 인증 조회 필요</option>{Array.from({length:10},(_,i)=>10-i).map(value=><option key={value}>{value}</option>)}</select></div><Field id="population" label="Population" value={form.population} disabled onChange={()=>{}}/></div>:<Options values={['민트','근민트','우량','양호','보통','하']} value={form.selfGrade} onChange={value=>setField('selfGrade',value)}/>}</fieldset></>;
}

function Field({id,label,value,onChange,inputMode=undefined,error='',disabled=false}){return <div><label htmlFor={id}>{label}</label><input id={id} value={value} inputMode={inputMode} disabled={disabled} onChange={e=>onChange(e.target.value)} aria-invalid={Boolean(error)}/>{error&&<ErrorText>{error}</ErrorText>}</div>}
function Options({values,value,onChange,className=''}){return <div className={'sell-condition-options '+className}>{values.map(item=><button type="button" key={item} className={value===item?'active':''} onClick={()=>onChange(item)}>{className==='duration'?item+'시간':item}</button>)}</div>}

function StepTwo({form,setField,photos,addPhotos,removePhoto,photoError}){
  return <><Title step="2" title="사진 및 설명" copy="상품 상태를 확인할 수 있는 사진과 설명을 등록하세요."/><div className="sell-photo-heading"><h3>상품 사진</h3><span>{photos.length} / 8장 등록</span></div><div className="sell-photo-grid">{Array.from({length:8},(_,index)=>{const photo=photos[index];if(photo)return <figure key={photo.id}><img src={photo.url} alt={'등록 사진 '+(index+1)}/>{index===0&&<figcaption>대표 이미지</figcaption>}<button type="button" onClick={()=>removePhoto(photo.id)} aria-label={(index+1)+'번 사진 삭제'}>×</button></figure>;if(index===photos.length)return <label key="upload" className="sell-photo-slot" htmlFor="product-photos"><b>+</b><span>사진 추가</span><input id="product-photos" type="file" accept="image/png,image/jpeg" multiple onChange={addPhotos}/></label>;return <div className="sell-photo-slot empty" key={index} aria-hidden="true"/>})}</div>{photoError&&<ErrorText>{photoError}</ErrorText>}<div className="sell-field-list"><label htmlFor="description">상품 상태 및 설명 <em>필수</em></label><textarea id="description" value={form.description} onChange={e=>setField('description',e.target.value)} placeholder="카드 상태, 보관 방법, 흠집 및 특이사항" required/><label htmlFor="seller-memo">판매자 메모</label><textarea id="seller-memo" value={form.sellerMemo} onChange={e=>setField('sellerMemo',e.target.value)} placeholder="구매자에게 전달할 추가 내용"/></div></>;
}

function MoneyInput({id,value,onChange}){return <div className="sell-money-input"><input id={id} inputMode="numeric" value={money(value)} onChange={e=>onChange(digits(e.target.value))}/><span>원</span></div>}
function StepThree({form,setField}){
  const buyError=form.buyNowEnabled&&form.buyNowPrice&&Number(form.buyNowPrice)<=Number(form.startPrice);
  const durationError=form.duration&&Number(form.duration)>24;
  return <><Title step="3" title="경매 설정" copy="가격과 시간, 배송 조건을 설정하세요."/><div className="sell-field-list"><label htmlFor="start-price">시작가 <em>필수</em></label><MoneyInput id="start-price" value={form.startPrice} onChange={value=>setField('startPrice',value)}/><label htmlFor="increment">호가 단위 <em>필수</em></label><MoneyInput id="increment" value={form.bidIncrement} onChange={value=>setField('bidIncrement',value)}/><div className="sell-quick-money">{[10000,50000,100000,500000,1000000].map(value=><button type="button" key={value} className={Number(form.bidIncrement)===value?'active':''} onClick={()=>setField('bidIncrement',String(value))}>{value.toLocaleString()}원</button>)}</div><Toggle id="buy-toggle" checked={form.buyNowEnabled} onChange={value=>setField('buyNowEnabled',value)} title="즉시 구매가" copy="구매자가 즉시 낙찰할 가격을 설정합니다."/>{form.buyNowEnabled&&<><label htmlFor="buy-price">즉시 구매 금액 <em>필수</em></label><MoneyInput id="buy-price" value={form.buyNowPrice} onChange={value=>setField('buyNowPrice',value)}/>{buyError&&<ErrorText>즉시 구매가는 시작가보다 커야 합니다.</ErrorText>}</>}<fieldset className="sell-grade-fieldset sell-duration-fieldset"><legend>경매 시간 <em>필수</em></legend><Options className="duration" values={['4','8','12']} value={form.duration} onChange={value=>setField('duration',value)}/><label htmlFor="duration-custom">직접 입력 <small>(최대 24시간)</small></label><div className="sell-duration-input"><input id="duration-custom" inputMode="numeric" value={form.duration} onChange={e=>setField('duration',digits(e.target.value))} aria-invalid={Boolean(durationError)}/><span>시간</span></div>{durationError&&<ErrorText>경매 시간은 최대 24시간까지 입력할 수 있습니다.</ErrorText>}</fieldset><div className="sell-shipping-field"><label htmlFor="shipping">배송비</label><MoneyInput id="shipping" value={form.shipping} onChange={value=>setField('shipping',value)}/></div></div></>;
}
function Toggle({id,checked,onChange,title,copy}){return <label className="sell-toggle-row" htmlFor={id}><span><b>{title}</b><small>{copy}</small></span><input id={id} type="checkbox" checked={checked} onChange={e=>onChange(e.target.checked)}/></label>}
function Review({form,photos,psaStatus,psaNumber,review,submitStatus,submitError}){return <><Title step="4" title="등록 내용 검토" copy="경매 등록 전 입력한 내용을 마지막으로 확인하세요."/>{psaStatus==='success'&&<div className="sell-psa-verified"><CheckCircle2/><span><b>PSA 인증 완료</b><small>인증번호 {psaNumber} · 위변조 검증 통과</small></span></div>}<dl className="sell-review-list">{review.map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><div className="sell-review-description"><h3>상품 상태 및 설명</h3><p>{form.description}</p>{form.sellerMemo&&<><h3>판매자 메모</h3><p>{form.sellerMemo}</p></>}</div><div className="sell-review-photos">{photos.map((photo,index)=><img src={photo.url} alt={'검토 사진 '+(index+1)} key={photo.id}/>)}</div>{submitError&&<div className="sell-submit-error" role="alert"><Info/><span><b>등록하지 못했습니다.</b><small>{submitError} 입력 내용을 확인하고 다시 시도해 주세요.</small></span></div>}{submitStatus==='success'&&<p className="form-success"><CheckCircle2/>경매 등록이 완료되었습니다. 상세 페이지로 이동합니다.</p>}</>}
