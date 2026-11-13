CREATE TABLE edu_teaching_building (
    id varchar(64) PRIMARY KEY,
    campus_id varchar(64) NOT NULL REFERENCES t_organization(id),
    building_code varchar(64) NOT NULL,
    building_name varchar(128) NOT NULL,
    UNIQUE (campus_id, building_code)
);

CREATE TABLE edu_teaching_floor (
    id varchar(64) PRIMARY KEY,
    building_id varchar(64) NOT NULL REFERENCES edu_teaching_building(id),
    floor_no integer NOT NULL,
    UNIQUE (building_id, floor_no)
);

ALTER TABLE edu_classroom ADD COLUMN floor_id varchar(64) REFERENCES edu_teaching_floor(id);
ALTER TABLE edu_classroom ADD COLUMN room_number varchar(64);
ALTER TABLE edu_classroom ADD CONSTRAINT uk_edu_classroom_floor_room UNIQUE (floor_id, room_number);
ALTER TABLE edu_classroom ADD CONSTRAINT ck_edu_classroom_location_pair
    CHECK ((floor_id IS NULL AND room_number IS NULL) OR (floor_id IS NOT NULL AND room_number IS NOT NULL));

-- 保留既有预约，阻止后续删除被预约引用的教室。
ALTER TABLE edu_classroom_reservation ADD CONSTRAINT fk_edu_classroom_reservation_room
    FOREIGN KEY (classroom_id) REFERENCES edu_classroom(id) NOT VALID;

UPDATE t_menu
   SET menu_name = '教室管理', path = '/admin/education/classroom-management'
 WHERE id = 'e4bcc456-fe06-4934-8aa9-be76e7cc8f57'
    OR (menu_name = '场地管理' AND coalesce(path, '') = '');
