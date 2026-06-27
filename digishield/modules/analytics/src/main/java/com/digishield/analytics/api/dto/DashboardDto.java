package com.digishield.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Aggregated dashboard payload powering the Admin dashboard in a single call.
 * <p>
 * Field names mirror the shapes consumed by {@code AdminDashboardPage}.
 *
 * @param riskScore          overall organisation risk score (0..100)
 * @param riskDelta          change versus the previous month (e.g. +3)
 * @param phishPronePct      organisation phish-prone percentage
 * @param phishPronePctDelta change in phish-prone percentage versus last month
 * @param industryAvgPct     industry (gov) average phish-prone percentage
 * @param trainingCompletion training completion percentage (0..100)
 * @param openAlerts         number of open alerts, by severity
 * @param riskTrend          90-day risk trend points (oldest first)
 * @param benchmarks         benchmark comparison bars
 * @param departments        per-department risk scores
 * @param recentReports      most recent phishing reports summary
 */
public record DashboardDto(
        @JsonProperty("risk_score") int riskScore,
        @JsonProperty("risk_delta") int riskDelta,
        @JsonProperty("phish_prone_pct") double phishPronePct,
        @JsonProperty("phish_prone_pct_delta") double phishPronePctDelta,
        @JsonProperty("industry_avg_pct") double industryAvgPct,
        @JsonProperty("training_completion") int trainingCompletion,
        @JsonProperty("open_alerts") OpenAlerts openAlerts,
        @JsonProperty("risk_trend") List<TrendPoint> riskTrend,
        @JsonProperty("benchmarks") List<Benchmark> benchmarks,
        @JsonProperty("departments") List<Department> departments,
        @JsonProperty("recent_reports") List<RecentReport> recentReports) {

    /**
     * Open alert counts by severity.
     */
    public record OpenAlerts(
            @JsonProperty("total") int total,
            @JsonProperty("critical") int critical,
            @JsonProperty("warning") int warning) {
    }

    /**
     * A single point on the 90-day risk trend line.
     */
    public record TrendPoint(
            @JsonProperty("date") String date,
            @JsonProperty("value") int value) {
    }

    /**
     * A benchmark comparison bar.
     */
    public record Benchmark(
            @JsonProperty("label") String label,
            @JsonProperty("value") double value,
            @JsonProperty("strong") boolean strong) {
    }

    /**
     * A department's risk score for the dashboard bars.
     */
    public record Department(
            @JsonProperty("name") String name,
            @JsonProperty("score") int score) {
    }

    /**
     * Compact recent-report row for the dashboard list.
     */
    public record RecentReport(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("who") String who,
            @JsonProperty("age") String age,
            @JsonProperty("ai_label") String aiLabel) {
    }
}
