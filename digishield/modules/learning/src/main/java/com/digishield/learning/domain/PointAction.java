package com.digishield.learning.domain;

/**
 * The things a person can do that move their score, with the points each is
 * worth by default.
 * <p>
 * The defaults match the rules the platform was designed around and shipped in
 * the dev seeder — reporting a real phish is worth ten times what clicking a
 * simulated one costs, because the product exists to encourage the first. A
 * tenant can override any of them with a {@code point_rule} row; these apply
 * when it has not.
 * <p>
 * Defaults live in code rather than only in seeded data on purpose: the rules
 * were seeded and never applied, so on a real tenant the table was empty and
 * nothing could have been awarded even once the wiring existed.
 */
public enum PointAction {

    /** Reported a genuine phishing message, confirmed by triage. */
    REPORT_CONFIRMED("report_confirmed", "Báo cáo email lừa đảo đúng", 50),
    /** Passed a course quiz. */
    QUIZ_PASSED("quiz_passed", "Đạt bài kiểm tra", 24),
    /** Finished an assigned course. */
    LESSON_COMPLETED("lesson_completed", "Hoàn thành bài học", 10),
    /** Clicked a simulated phishing link. */
    SIMULATION_CLICKED("simulation_clicked", "Bấm link mô phỏng", -5);

    private final String wireName;
    private final String label;
    private final int defaultPoints;

    PointAction(String wireName, String label, int defaultPoints) {
        this.wireName = wireName;
        this.label = label;
        this.defaultPoints = defaultPoints;
    }

    /** Identifier stored in {@code point_rule.action} and shown on the wire. */
    public String wireName() {
        return wireName;
    }

    /** Human-readable description, used when a tenant has no rule of its own. */
    public String label() {
        return label;
    }

    public int defaultPoints() {
        return defaultPoints;
    }
}
