-- 旧试卷题目无法安全推断当时引用的版本，保留 NULL 供人工复核。
ALTER TABLE edu_exam_paper_item
    ADD COLUMN question_version_id varchar(64),
    ADD COLUMN question_snapshot_hash varchar(64),
    ADD CONSTRAINT fk_edu_exam_paper_question_version
        FOREIGN KEY (question_version_id) REFERENCES edu_question_version(id),
    ADD CONSTRAINT ck_edu_exam_paper_question_version_pin
        CHECK ((question_version_id IS NULL AND question_snapshot_hash IS NULL)
            OR (question_version_id IS NOT NULL AND length(question_snapshot_hash) = 64));

CREATE INDEX idx_edu_exam_paper_item_question_version
    ON edu_exam_paper_item(question_version_id);
