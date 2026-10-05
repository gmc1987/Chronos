-- 普通高中和小学没有专业，行政班及学生学籍允许不关联中职专业目录。
ALTER TABLE edu_administrative_class ALTER COLUMN major_id DROP NOT NULL;
ALTER TABLE edu_student_profile ALTER COLUMN major_id DROP NOT NULL;
