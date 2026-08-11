# Notification 개발 계획

Notification은 경매 생성/상회 입찰/낙찰 이벤트를 구독해 알림을 쌓고, 사용자에게 목록으로 보여준다. 실시간 푸시는 SSE로 구현했다(4단계) — WebSocket/FCM은 여전히 범위 밖.

## 구현 단계

1. [Notification 엔티티 + 목록조회 골격](1-entity-and-list.md)
2. [읽음 상태, 목록 API 분리, 이동 기능, 인증 전환](2-read-status-and-navigation.md)
3. [프론트엔드 연동 계획 (SSE 제외)](3-frontend-integration-plan.md)
4. [알림 실시간 푸시(SSE) 연동 계획](4-realtime-sse.md) — 백엔드 구현 완료(이슈 #161 / PR #167)
5. [알림 실시간 푸시(SSE) 프론트엔드 연동 계획](5-frontend-sse-integration.md) — 설계 중, 피드백 대기
6. [알림 저장 소실 복구 배치 설계](6-notification-recovery-batch.md) — 구현 완료(이슈 #189 / PR #193)
7. [경매 생성 알림 fan-out을 batch insert로 개선](7-notification-fanout-batch-insert.md) — 구현 완료(이슈 #190), PR 진행 중
8. [NotificationEventListener 전용 Executor 지정 및 SSE push 릴레이 구조 분리](8-notification-sse-async-executor-and-push-relay.md) — 이슈 #239, Redis 전환은 #281로 분리
9. [복구 배치의 경매 생성 알림 fan-out을 saveAllIgnoringDuplicates로 전환](9-recovery-batch-wishlist-fanout-optimization.md) — 이슈 #306
12. [알림 fan-out batch insert를 청크로 나눠 프리페어드 스테이트먼트 한도 방지](12-notification-fanout-chunk-insert-plan.md) — 이슈 #207
13. [위시리스트 fan-out SSE push를 배치 발행 1번으로 묶기](13-notification-batch-push-plan.md) — 이슈 #289
14. [Notification origin/subscriber TaskExecutor 분리](14-notification-executor-split-plan.md) — 이슈 #305
15. [스케줄러 on/off 프로퍼티 키를 application.yml에 명시](15-scheduler-toggle-properties.md) — 이슈 #366, 구현 완료

> 이 문서는 claude의 도움을 받아 작성하였습니다.