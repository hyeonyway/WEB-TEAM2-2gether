import {ChevronRight,Gavel} from 'lucide-react';

type Props={
  step:number;
  canContinue:boolean;
  submitStatus:string;
  onPrevious:()=>void;
  onNext:()=>void;
  onSubmit:()=>void;
};

export default function SellStepActions({step,canContinue,submitStatus,onPrevious,onNext,onSubmit}:Props){
  const submitting=submitStatus==='loading';
  return <div className="sell-step-actions">
    {step>1&&<button type="button" onClick={onPrevious} disabled={submitting}>이전</button>}
    {step<4
      ? <button type="button" className="primary" disabled={!canContinue} onClick={onNext}>다음 단계 <ChevronRight/></button>
      : <button type="button" className="primary" disabled={submitting||submitStatus==='success'} onClick={onSubmit}>
          <Gavel/>{submitting?'등록 중…':'경매 등록'}
        </button>}
  </div>;
}
