package com.hongjie.pms.common.circuitbreaker;

import lombok.Data;

@Data
public class SlidingWindow {

    private final int windowSize;
    private final int bucketCount;
    private final long bucketTimeMs;

    private final Bucket[] buckets;
    private long lastTimestamp;

    public SlidingWindow(int windowSize, int bucketCount) {
        this.windowSize = windowSize;
        this.bucketCount = bucketCount;
        this.bucketTimeMs = windowSize * 1000L / bucketCount;
        this.buckets = new Bucket[bucketCount];
        this.lastTimestamp = System.currentTimeMillis();

        for (int i = 0; i < bucketCount; i++) {
            buckets[i] = new Bucket();
        }
    }

    public synchronized void record(boolean success, long responseTime) {
        long now = System.currentTimeMillis();
        slideWindow(now);

        int bucketIndex = (int) ((now / bucketTimeMs) % bucketCount);
        Bucket bucket = buckets[bucketIndex];

        bucket.totalCount++;
        if (success) {
            bucket.successCount++;
        } else {
            bucket.failureCount++;
        }
        bucket.totalResponseTime += responseTime;
        bucket.maxResponseTime = Math.max(bucket.maxResponseTime, responseTime);
    }

    private void slideWindow(long now) {
        long currentWindowStart = (now / bucketTimeMs) * bucketTimeMs;
        long lastWindowStart = (lastTimestamp / bucketTimeMs) * bucketTimeMs;

        int slotsToSlide = (int) ((currentWindowStart - lastWindowStart) / bucketTimeMs);

        if (slotsToSlide >= bucketCount) {
            // 全部清空
            for (int i = 0; i < bucketCount; i++) {
                buckets[i] = new Bucket();
            }
        } else if (slotsToSlide > 0) {
            // 滑动指定数量的槽位
            for (int i = 0; i < slotsToSlide; i++) {
                int indexToClear = (int) ((lastWindowStart / bucketTimeMs + i) % bucketCount);
                buckets[indexToClear] = new Bucket();
            }
        }

        lastTimestamp = now;
    }

    public StatData getStatData() {
        long now = System.currentTimeMillis();
        slideWindow(now);

        StatData stat = new StatData();

        for (Bucket bucket : buckets) {
            stat.totalCount += bucket.totalCount;
            stat.successCount += bucket.successCount;
            stat.failureCount += bucket.failureCount;
            stat.totalResponseTime += bucket.totalResponseTime;
            stat.maxResponseTime = Math.max(stat.maxResponseTime, bucket.maxResponseTime);
        }

        stat.errorRate = stat.totalCount > 0
                ? (double) stat.failureCount / stat.totalCount
                : 0.0;
        stat.avgResponseTime = stat.totalCount > 0
                ? stat.totalResponseTime / stat.totalCount
                : 0;

        return stat;
    }

    @Data
    static class Bucket {
        int totalCount = 0;
        int successCount = 0;
        int failureCount = 0;
        long totalResponseTime = 0;
        long maxResponseTime = 0;
    }

    @Data
    public static class StatData {
        int totalCount = 0;
        int successCount = 0;
        int failureCount = 0;
        long totalResponseTime = 0;
        long maxResponseTime = 0;
        double errorRate = 0.0;
        long avgResponseTime = 0;

        @Override
        public String toString() {
            return String.format("总请求=%d, 成功=%d, 失败=%d, 错误率=%.2f%%, 平均耗时=%dms, 最大耗时=%dms",
                    totalCount, successCount, failureCount, errorRate * 100, avgResponseTime, maxResponseTime);
        }
    }
}