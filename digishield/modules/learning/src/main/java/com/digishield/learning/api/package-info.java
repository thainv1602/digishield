/**
 * Public API of the learning module ({@code LearningService} + its view types).
 * Exposed as a Spring Modulith named interface so the application shell can read
 * training data across the module boundary (e.g. enrollment completion for the
 * analytics dashboard), mirroring the reporting module's exposed {@code api}.
 */
@org.springframework.modulith.NamedInterface("api")
package com.digishield.learning.api;
