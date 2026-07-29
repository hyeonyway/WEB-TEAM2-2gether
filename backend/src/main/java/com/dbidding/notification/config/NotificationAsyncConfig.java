package com.dbidding.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// TODO: global/config/AsyncConfig가 생기면 (다른 도메인도 @Async를 쓰게 되면) 그쪽으로 통합하고 이 파일은 삭제.
@Configuration
@EnableAsync
public class NotificationAsyncConfig {
}