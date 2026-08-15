package com.dbidding.sse;

/** 호출 스레드에서 sendTask를 그대로 실행한다(#362 이전 동작, 비교/롤백용 baseline). */
public class SynchronousSseSendDispatcher implements SseSendDispatcher {
    @Override
    public void dispatch(Runnable sendTask) {
        sendTask.run();
    }
}
