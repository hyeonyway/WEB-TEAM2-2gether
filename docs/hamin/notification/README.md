# Notification 개발 계획

Notification은 경매 생성/상회 입찰/낙찰 이벤트를 구독해 알림을 쌓고, 사용자에게 목록으로 보여준다. 실시간 푸시는 SSE로 구현했다(4단계) — WebSocket/FCM은 여전히 범위 밖.

## 구현 단계

1. [Notification 엔티티 + 목록조회 골격](1-entity-and-list.md)
2. [읽음 상태, 목록 API 분리, 이동 기능, 인증 전환](2-read-status-and-navigation.md)
3. [프론트엔드 연동 계획 (SSE 제외)](3-frontend-integration-plan.md)
4. [알림 실시간 푸시(SSE) 연동 계획](4-realtime-sse.md) — 백엔드 구현 완료(이슈 #161 / PR #167)
5. [알림 실시간 푸시(SSE) 프론트엔드 연동 계획](5-frontend-sse-integration.md) — 설계 중, 피드백 대기
6. [알림 저장 소실 복구 배치 설계](6-notification-recovery-batch.md) — 설계 완료, 구현 전

> 이 문서는 claude의 도움을 받아 작성하였습니다.