package com.dbidding.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class PerConnectorTomcatThreadMetricsTest {

    @Test
    void 관리_커넥터의_Tomcat_스레드_메트릭을_등록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PerConnectorTomcatThreadMetrics metrics = new PerConnectorTomcatThreadMetrics(registry, 9091);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 4, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        ProtocolHandler protocolHandler = mock(ProtocolHandler.class);
        when(protocolHandler.getExecutor()).thenReturn(executor);
        Connector connector = mock(Connector.class);
        when(connector.getProtocolHandler()).thenReturn(protocolHandler);
        when(connector.getPort()).thenReturn(9091);

        metrics.bind(connector);

        assertThat(registry.get("tomcat.connector.threads.busy").tag("connector", "management").gauge().value()).isZero();
        assertThat(registry.get("tomcat.connector.threads.current").tag("connector", "management").gauge().value()).isEqualTo(executor.getPoolSize());
        assertThat(registry.get("tomcat.connector.threads.max").tag("connector", "management").gauge().value()).isEqualTo(4);
    }
}
