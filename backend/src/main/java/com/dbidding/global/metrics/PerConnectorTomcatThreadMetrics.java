package com.dbidding.global.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class PerConnectorTomcatThreadMetrics implements ApplicationListener<WebServerInitializedEvent> {
    private final MeterRegistry registry;
    private final int managementPort;

    public PerConnectorTomcatThreadMetrics(
            MeterRegistry registry,
            @Value("${management.server.port:9091}") int managementPort
    ) {
        this.registry = registry;
        this.managementPort = managementPort;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!(event.getWebServer() instanceof TomcatWebServer tomcatWebServer)) {
            return;
        }
        for (Connector connector : tomcatWebServer.getTomcat().getService().findConnectors()) {
            bind(connector);
        }
    }

    void bind(Connector connector) {
        Executor executor = connector.getProtocolHandler().getExecutor();
        if (!(executor instanceof ThreadPoolExecutor threadPool)) {
            return;
        }
        String connectorName = connector.getPort() == managementPort ? "management" : "main";
        register("tomcat.connector.threads.busy", connectorName, threadPool, ThreadPoolExecutor::getActiveCount);
        register("tomcat.connector.threads.current", connectorName, threadPool, ThreadPoolExecutor::getPoolSize);
        register("tomcat.connector.threads.max", connectorName, threadPool, ThreadPoolExecutor::getMaximumPoolSize);
    }

    private void register(
            String name,
            String connectorName,
            ThreadPoolExecutor threadPool,
            java.util.function.ToDoubleFunction<ThreadPoolExecutor> value
    ) {
        Gauge.builder(name, threadPool, value)
                .tag("connector", connectorName)
                .description("Tomcat 커넥터별 스레드 수")
                .register(registry);
    }
}
