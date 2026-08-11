package com.digishield.learning.application;

import com.digishield.learning.api.AssessmentResultView;
import com.digishield.learning.api.LessonSummaryView;
import com.digishield.learning.domain.Course;
import com.digishield.learning.domain.CourseLevel;
import com.digishield.learning.domain.Lesson;
import com.digishield.learning.domain.QuizQuestion;
import com.digishield.learning.infrastructure.CourseRepository;
import com.digishield.learning.infrastructure.LessonRepository;
import com.digishield.learning.infrastructure.QuizQuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Quiz scoring — the number a learner is judged by.
 *
 * <p>Getting it wrong is invisible from the outside: a pass mark applied as
 * {@code >} instead of {@code >=} fails everyone who scored exactly the
 * threshold, and an unanswered question counted as correct passes people who
 * answered nothing at all.
 */
@ExtendWith(MockitoExtension.class)
class QuizScoringTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LESSON = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LessonRepository lessonRepository;

    private LearningServiceImpl service;

    @BeforeEach
    void setUp() {
        // 21 collaborators, one of which this test needs. Passing null for the
        // rest is deliberate: a scoring test that also had to satisfy twenty
        // mocks would be testing the wiring, not the arithmetic.
        service = new LearningServiceImpl(
                courseRepository, null, lessonRepository, quizQuestionRepository,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, new ObjectMapper(), 3);
    }

    private QuizQuestion question(UUID id, int correctIndex, String explanation) {
        return new QuizQuestion(id, TENANT, LESSON, "Đây có phải phishing?",
                "A", "B", "C", "D", correctIndex, explanation, 1);
    }

    private void givenQuestions(QuizQuestion... questions) {
        when(quizQuestionRepository.findByTenantIdAndLessonIdOrderBySortOrderAsc(TENANT, LESSON))
                .thenReturn(List.of(questions));
    }

    @Test
    @DisplayName("70% exactly is a pass, not a near miss")
    void theThresholdItselfPasses() {
        UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                      UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        QuizQuestion[] qs = new QuizQuestion[10];
        for (int i = 0; i < 10; i++) {
            qs[i] = question(ids[i], 1, "vì thế");
        }
        givenQuestions(qs);
        // Seven of ten right: exactly the pass mark.
        Map<String, Integer> answers = new java.util.HashMap<>();
        for (int i = 0; i < 10; i++) {
            answers.put(ids[i].toString(), i < 7 ? 1 : 0);
        }

        AssessmentResultView result = service.submitResponses(TENANT, LESSON, answers);

        assertThat(result.score()).isEqualTo(7);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("an unanswered question is wrong, not free")
    void missingAnswersDoNotCount() {
        UUID id = UUID.randomUUID();
        givenQuestions(question(id, 2, "đáp án đúng là C"));

        AssessmentResultView result = service.submitResponses(TENANT, LESSON, Map.of());

        assertThat(result.score()).isZero();
        assertThat(result.passed()).isFalse();
        assertThat(result.review()).singleElement()
                .satisfies(row -> assertThat(row.correct()).isFalse());
    }

    @Test
    @DisplayName("a null answer map scores zero rather than throwing")
    void noAnswersAtAll() {
        givenQuestions(question(UUID.randomUUID(), 0, "giải thích"));

        AssessmentResultView result = service.submitResponses(TENANT, LESSON, null);

        assertThat(result.score()).isZero();
        assertThat(result.passed()).isFalse();
    }

    @Test
    @DisplayName("answers keyed by position work as well as by question id")
    void positionalKeysAreAccepted() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        givenQuestions(question(first, 1, "e1"), question(second, 3, "e2"));

        // The client may not know the ids; q1/q2 address them in order.
        AssessmentResultView result = service.submitResponses(
                TENANT, LESSON, Map.of("q1", 1, "q2", 3));

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("the explanation is returned only for what was got wrong")
    void reviewExplainsOnlyMistakes() {
        UUID right = UUID.randomUUID();
        UUID wrong = UUID.randomUUID();
        givenQuestions(question(right, 1, "không cần"), question(wrong, 2, "đáp án đúng là C"));

        AssessmentResultView result = service.submitResponses(
                TENANT, LESSON, Map.of(right.toString(), 1, wrong.toString(), 0));

        assertThat(result.review()).hasSize(2);
        assertThat(result.review().get(0).explain()).isEmpty();
        assertThat(result.review().get(1).explain()).isEqualTo("đáp án đúng là C");
    }

    @Test
    @DisplayName("a lesson with no quiz is not a pass")
    void anEmptyQuizCannotBePassed() {
        givenQuestions();

        AssessmentResultView result = service.submitResponses(TENANT, LESSON, Map.of());

        assertThat(result.total()).isZero();
        // 0/0 is not 100%: nothing was demonstrated.
        assertThat(result.passed()).isFalse();
    }

    @Test
    @DisplayName("a lesson listing carries its course's title and its own question count")
    void lessonsAreListedWithTheirContext() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID orphanLessonId = UUID.randomUUID();
        when(courseRepository.findByTenantId(TENANT)).thenReturn(List.of(
                new Course(courseId, TENANT, "Nhận biết phishing", CourseLevel.BASIC, "vi")));
        when(lessonRepository.findByTenantIdOrderBySortOrderAsc(TENANT)).thenReturn(List.of(
                new Lesson(lessonId, TENANT, courseId, "Bài 1", "body",
                        null, null, null, null, 15, 1),
                // A lesson whose course is missing must still list, without a title.
                new Lesson(orphanLessonId, TENANT, UUID.randomUUID(), "Bài mồ côi", "body",
                        null, null, null, null, 5, 2)));
        when(quizQuestionRepository.findByTenantIdAndLessonIdOrderBySortOrderAsc(TENANT, lessonId))
                .thenReturn(List.of(question(UUID.randomUUID(), 1, "e")));
        when(quizQuestionRepository
                .findByTenantIdAndLessonIdOrderBySortOrderAsc(TENANT, orphanLessonId))
                .thenReturn(List.of());

        List<LessonSummaryView> lessons = service.listLessons(TENANT);

        assertThat(lessons).extracting(LessonSummaryView::courseTitle)
                .containsExactly("Nhận biết phishing", null);
        assertThat(lessons).extracting(LessonSummaryView::questionCount)
                .containsExactly(1, 0);
        assertThat(lessons.getFirst().durationMin()).isEqualTo(15);
    }
}
