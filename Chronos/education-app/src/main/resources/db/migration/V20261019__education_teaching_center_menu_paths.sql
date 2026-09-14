-- Wire the persisted education menus to their dedicated pages.
UPDATE t_menu
SET path = '/admin/education/teaching-center/preparation'
WHERE id = 'f590ec80-c31f-4f42-a5a3-7fef4a17785f' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/courseware'
WHERE id = '408cf6a4-982a-4f22-bcf0-35a6ce6a008d' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/homework'
WHERE id = 'beeae9a6-2bf0-45f8-ac29-95bc36f22ce3' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/question-bank'
WHERE id = '26188f82-180c-447b-849a-a19d1c32034b' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/knowledge-point'
WHERE id = 'f73673c6-6854-4177-b518-8e0f94d1eb63' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/error-book'
WHERE id = '1ced643e-0c2b-456d-9b4f-50e3afb83b9d' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/research'
WHERE id = 'b215832d-4ea7-4d55-885d-079a8fb2c829' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/plan'
WHERE id = 'd2dbbc4e-c9c5-4463-a56b-c605a53b288a' AND (path IS NULL OR path = '');

UPDATE t_menu
SET path = '/admin/education/teaching-center/lesson-plan'
WHERE id = '9a6bb5e8-3e18-4623-9f9a-8ae4c4838f9e' AND (path IS NULL OR path = '');
