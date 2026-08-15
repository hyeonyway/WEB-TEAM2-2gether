package com.dbidding.sse;

/**
 * 브로드캐스트/push 안에서 emitter별 send를 호출 스레드에서 순차 실행할지, 커넥션 1개당
 * 독립 task로 세분화할지를 결정한다(#362, #508로 auction 패키지에서 승격).
 */
public interface SseSendDispatcher {
    void dispatch(Runnable sendTask);
}
