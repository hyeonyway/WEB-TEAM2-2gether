package com.dbidding.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MeasureTimeAspectTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MeasureTimeAspect aspect = new MeasureTimeAspect(registry);

    @Test
    void records_successful_execution() throws Throwable {
        Object result = aspect.measure(new ProceedingJoinPointStub(() -> "ok"), annotation());

        assertThat(result).isEqualTo("ok");
        assertThat(registry.get("dbidding.method.duration")
                .tag("operation", "test.operation")
                .tag("result", "success")
                .timer().count()).isOne();
    }

    @Test
    void records_failure_and_rethrows_exception() {
        RuntimeException failure = new RuntimeException("failure");

        assertThatThrownBy(() -> aspect.measure(new ProceedingJoinPointStub(() -> {
            throw failure;
        }), annotation())).isSameAs(failure);

        assertThat(registry.get("dbidding.method.duration")
                .tag("operation", "test.operation")
                .tag("result", "failure")
                .timer().count()).isOne();
    }

    private MeasureTime annotation() {
        return (MeasureTime) Proxy.newProxyInstance(
                MeasureTime.class.getClassLoader(),
                new Class<?>[]{MeasureTime.class},
                (proxy, method, args) -> method.getName().equals("value")
                        ? "test.operation" : method.getDefaultValue());
    }

    @FunctionalInterface
    private interface Action { Object run() throws Throwable; }

    private record ProceedingJoinPointStub(Action action)
            implements org.aspectj.lang.ProceedingJoinPoint {
        @Override public Object proceed() throws Throwable { return action.run(); }
        @Override public Object proceed(Object[] args) throws Throwable { return action.run(); }
        @Override public void set$AroundClosure(org.aspectj.runtime.internal.AroundClosure arc) { }
        @Override public org.aspectj.lang.JoinPoint.StaticPart getStaticPart() { return null; }
        @Override public org.aspectj.lang.Signature getSignature() { return null; }
        @Override public org.aspectj.lang.JoinPoint getThis() { return this; }
        @Override public org.aspectj.lang.JoinPoint getTarget() { return this; }
        @Override public Object[] getArgs() { return new Object[0]; }
        @Override public org.aspectj.lang.reflect.SourceLocation getSourceLocation() { return null; }
        @Override public String getKind() { return "method-execution"; }
        @Override public String toShortString() { return "stub"; }
        @Override public String toLongString() { return "stub"; }
    }
}
