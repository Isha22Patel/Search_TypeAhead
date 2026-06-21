package com.typeahead.aop;

import com.typeahead.dto.MetricsDTO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * AOP Aspect that intercepts all controller method calls to measure latency.
 *
 * Why AOP instead of a Filter or Interceptor?
 *   - @Around advice wraps the exact method invocation — no overhead from
 *     Spring's servlet dispatch chain or view resolution.
 *   - Applies selectively to controller classes only (not services/repositories).
 *   - No modification to controller code required.
 *
 * Percentile calculation:
 *   Uses a rolling window of the last 1000 samples per endpoint.
 *   CopyOnWriteArrayList is used for thread-safe snapshot access.
 *   Samples are sorted at read time (on /metrics call), not at write time,
 *   so search/suggest calls have no sorting overhead.
 *
 *   p50 = 50th percentile (median latency)
 *   p95 = 95th percentile (tail latency — important for SLA)
 *   p99 = 99th percentile (worst 1% of requests)
 */
@Slf4j
@Aspect
@Component
public class LatencyAspect {

    /** Maximum samples kept per endpoint in the rolling window. */
    private static final int WINDOW_SIZE = 1000;

    /**
     * Per-endpoint latency sample storage.
     * Key = controller class simple name (e.g., "SuggestController").
     * Value = list of latency measurements in nanoseconds.
     */
    private final ConcurrentHashMap<String, List<Long>> latencySamples =
        new ConcurrentHashMap<>();

    /** Total request counter across all endpoints. */
    private long totalRequests = 0;

    /**
     * Pointcut: intercepts all public methods in any class inside the
     * com.typeahead.controller package.
     *
     * @Around wraps the method — we measure time before and after proceed().
     */
    @Around("execution(* com.typeahead.controller..*(..))")
    public Object measureLatency(ProceedingJoinPoint joinPoint) throws Throwable {
        String controllerName = joinPoint.getTarget().getClass().getSimpleName();

        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            return result;
        } finally {
            long elapsedNanos = System.nanoTime() - startNanos;
            recordSample(controllerName, elapsedNanos);
            synchronized (this) { totalRequests++; }
            log.debug("[{}] latency: {}ms", controllerName, elapsedNanos / 1_000_000.0);
        }
    }

    /** Records a latency sample, evicting the oldest if window is full. */
    private void recordSample(String controller, long nanos) {
        List<Long> samples = latencySamples.computeIfAbsent(
            controller, k -> Collections.synchronizedList(new ArrayList<>(WINDOW_SIZE))
        );
        synchronized (samples) {
            if (samples.size() >= WINDOW_SIZE) {
                samples.remove(0); // Evict oldest (FIFO)
            }
            samples.add(nanos);
        }
    }

    /**
     * Computes latency stats for a given controller name.
     * Called by MetricsController.
     */
    public MetricsDTO.LatencyStats getStats(String controllerName) {
        List<Long> samples = latencySamples.get(controllerName);
        if (samples == null || samples.isEmpty()) {
            return MetricsDTO.LatencyStats.builder()
                .p50Ms(0).p95Ms(0).p99Ms(0).sampleCount(0).build();
        }

        List<Long> sorted;
        synchronized (samples) {
            sorted = new ArrayList<>(samples);
        }
        Collections.sort(sorted);

        return MetricsDTO.LatencyStats.builder()
            .p50Ms(percentileMs(sorted, 0.50))
            .p95Ms(percentileMs(sorted, 0.95))
            .p99Ms(percentileMs(sorted, 0.99))
            .sampleCount(sorted.size())
            .build();
    }

    public long getTotalRequests() { return totalRequests; }

    /** Converts nanosecond percentile to milliseconds (double precision). */
    private double percentileMs(List<Long> sortedNanos, double percentile) {
        int idx = (int) Math.ceil(percentile * sortedNanos.size()) - 1;
        idx = Math.max(0, Math.min(idx, sortedNanos.size() - 1));
        return sortedNanos.get(idx) / 1_000_000.0;
    }
}
