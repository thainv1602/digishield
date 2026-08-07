-- Deadline for a training assignment.
--
-- Until now an enrollment had no due date, so nothing could say whether someone
-- was late: EnrollmentStatus.OVERDUE existed and was never set, and the learner
-- portal grouped tasks by an urgency it derived from a status that never became
-- overdue.
--
-- Nullable on purpose. Rows written before this column existed have no deadline
-- and must not acquire one retroactively -- backfilling would declare people
-- late for a rule that did not exist when they were assigned the course.
alter table enrollment
    add column if not exists due_at timestamptz;

-- The overdue sweep looks for assignments past their date that are not finished.
create index if not exists idx_enrollment_due_at
    on enrollment (due_at)
    where due_at is not null;
