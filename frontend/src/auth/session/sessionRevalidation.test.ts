import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {clearCsrfToken,getCsrfToken,setCsrfToken} from './csrfTokenStore';
import {getSessionUserId,setSession} from './sessionAuthStore';
import {revalidateSession} from './sessionRevalidation';

function jsonResponse(body:unknown,status=200){
  return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
}

describe('revalidateSession',()=>{
  beforeEach(()=>{
    setSession(37);
    setCsrfToken('existing-csrf-token');
  });
  afterEach(()=>{vi.restoreAllMocks();setSession(null);clearCsrfToken();});

  it('세션이 살아있으면 true를 반환하고 상태를 유지한다',async()=>{
    vi.spyOn(globalThis,'fetch').mockResolvedValueOnce(jsonResponse({userId:37}));

    const result=await revalidateSession();

    expect(result).toBe(true);
    expect(getSessionUserId()).toBe(37);
    expect(getCsrfToken()).toBe('existing-csrf-token');
  });

  it('세션이 죽었으면 false를 반환하고 세션 상태를 비운다',async()=>{
    vi.spyOn(globalThis,'fetch').mockResolvedValueOnce(jsonResponse({code:'SESSION_EXPIRED'},401));

    const result=await revalidateSession();

    expect(result).toBe(false);
    expect(getSessionUserId()).toBeNull();
    expect(getCsrfToken()).toBeNull();
  });
});
