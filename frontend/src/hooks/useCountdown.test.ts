import {act,renderHook} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {displayRemaining,formatRemaining,isAuctionEnded,useCountdownNow} from './useCountdown';

describe('formatRemaining',()=>{
  it('남은 시간을 HH:MM:SS로 표시한다',()=>{
    const now=Date.parse('2026-08-08T00:00:00Z');
    const endsAt='2026-08-08T01:02:03Z';

    expect(formatRemaining(endsAt,now)).toBe('01:02:03');
  });

  it('종료 시각이 지나면 경매_종료를 표시한다',()=>{
    const now=Date.parse('2026-08-08T01:00:00Z');
    const endsAt='2026-08-08T00:00:00Z';

    expect(formatRemaining(endsAt,now)).toBe('경매 종료');
  });
});

describe('isAuctionEnded',()=>{
  it('OPEN이고 시간이 남았으면 종료가 아니다',()=>{
    expect(isAuctionEnded('OPEN','00:10:00')).toBe(false);
  });

  it('상태가 OPEN/ENDING이 아니면 종료다',()=>{
    expect(isAuctionEnded('ENDED','00:10:00')).toBe(true);
  });

  it('남은 시간이 경매_종료면 종료다',()=>{
    expect(isAuctionEnded('OPEN','경매 종료')).toBe(true);
  });
});

describe('displayRemaining',()=>{
  it('ENDING이면 남은시간과 무관하게 마감임박을 표시한다',()=>{
    const now=Date.parse('2026-08-08T00:00:00Z');

    expect(displayRemaining('ENDING','2026-08-08T01:00:00Z',now)).toBe('마감임박');
  });

  it('ENDING이어도 실제 마감시각이 지나면 경매 종료를 표시한다',()=>{
    const now=Date.parse('2026-08-08T01:00:00Z');

    expect(displayRemaining('ENDING','2026-08-08T00:00:00Z',now)).toBe('경매 종료');
  });
});

describe('useCountdownNow',()=>{
  beforeEach(()=>vi.useFakeTimers());
  afterEach(()=>vi.useRealTimers());

  it('1초마다 now를 갱신한다',()=>{
    vi.setSystemTime(new Date('2026-08-08T00:00:00Z'));
    const{result}=renderHook(()=>useCountdownNow());
    const initial=result.current;

    act(()=>vi.advanceTimersByTime(1000));

    expect(result.current).toBe(initial+1000);
  });
});
