const labels=['카드 정보','사진·설명','경매 설정','검토'];

export default function SellProgress({step}:{step:number}){
  return <ol className="sell-stepper">
    {labels.map((label,index)=>{
      const number=index+1;
      const state=number<step?'complete':number===step?'current':'upcoming';
      return <li className={state} key={label} aria-current={number===step?'step':undefined}>
        <b>{number<step?'✓':number}</b>
        <span>{label}</span>
      </li>;
    })}
  </ol>;
}
