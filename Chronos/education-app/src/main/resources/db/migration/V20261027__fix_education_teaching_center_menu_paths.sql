-- Correct previously persisted education menu paths even when an older value
-- was already present, so the dedicated frontend routes are reachable.
UPDATE t_menu
SET path = '/admin/education/teaching-center/preparation'
WHERE id = 'f590ec80-c31f-4f42-a5a3-7fef4a17785f';

UPDATE t_menu
SET path = '/admin/education/teaching-center/courseware'
WHERE id = '408cf6a4-982a-4f22-bcf0-35a6ce6a008d';

UPDATE t_menu
SET path = '/admin/education/teaching-center/question-bank'
WHERE id = '26188f82-180c-447b-849a-a19d1c32034b';

UPDATE t_menu
SET path = '/admin/education/teaching-center/research'
WHERE id = 'b215832d-4ea7-4d55-885d-079a8fb2c829';
