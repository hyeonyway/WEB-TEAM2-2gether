package com.dbidding.global.metrics;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Aspect
@Component
@Order(0)
public class MeasureTimeAspect {

    private final MeterRegistry meterRegistry;

    public MeasureTimeAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(measureTime)")
    public Object measure(ProceedingJoinPoint joinPoint, MeasureTime measureTime) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Object result = joinPoint.proceed();
            stop(sample, measureTime.value(), "success");
            return result;
        } catch (Throwable throwable) {
            stop(sample, measureTime.value(), "failure");
            throw throwable;
        }
    }

    private void stop(Timer.Sample sample, String operation, String result) {
        sample.stop(Timer.builder("dbidding.method.duration")
                .description("애노테이션이 적용된 메서드의 처리시간")
                .tag("operation", operation)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }
}
