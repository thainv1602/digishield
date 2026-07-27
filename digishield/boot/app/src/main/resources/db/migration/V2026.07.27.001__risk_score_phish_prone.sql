-- Carry the phish-prone rate alongside the risk score on rolled-up rows.
--
-- The dashboard used to derive this rate from the risk score with a fixed
-- factor (risk * 0.135), and fell back to a constant 8.4 when there was no
-- score. Both numbers were invented. The real rate is measured — the share of a
-- group's people who clicked a simulation — but it cannot be recovered from the
-- score, so the rollup has to store it.
--
-- Nullable on purpose: rows written before this migration have no measured
-- rate, and guessing one for them would recreate the problem being removed.
ALTER TABLE risk_score
    ADD COLUMN IF NOT EXISTS phish_prone_pct DOUBLE PRECISION;

COMMENT ON COLUMN risk_score.phish_prone_pct IS
    'Measured share (%) of the scope''s people who clicked a simulation in the scoring window; NULL for rows predating the rollup job.';
