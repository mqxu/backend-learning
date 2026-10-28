-- ============================================================
-- 演示数据：分类、组织、账号、学籍白名单、示例活动
-- 执行顺序：先 schema.sql，再 data.sql
-- 时间字段用 NOW() 计算，保证执行后活动处于“进行中/可报名”状态
-- ============================================================

SET NAMES utf8mb4;

DELETE FROM audit_record;
DELETE FROM sys_notice;
DELETE FROM activity_favorite;
DELETE FROM activity_checkin;
DELETE FROM activity_signup;
DELETE FROM activity;
DELETE FROM activity_category;
DELETE FROM org_member;
DELETE FROM organization;
DELETE FROM student_whitelist;
DELETE FROM student_profile;
DELETE FROM sys_user;

-- 分类
INSERT INTO activity_category (id, name, code, icon, sort, status) VALUES
(1001, '文体活动', 'SPORT',   '#2563eb', 1, 1),
(1002, '学术讲座', 'LECTURE', '#7c3aed', 2, 1),
(1003, '志愿服务', 'VOLUNTEER', '#059669', 3, 1),
(1004, '社团招新', 'CLUB',    '#d97706', 4, 1),
(1005, '创新创业', 'INNOVATE', '#dc2626', 5, 1),
(1006, '社会实践', 'PRACTICE', '#0891b2', 6, 1);

-- 组织
INSERT INTO organization (id, name, type, logo_url, description, contact, status) VALUES
(2001, '计算机学院学生会', 2, NULL, '计算机学院学生活动组织与执行', '张老师', 1),
(2002, '青年志愿者协会', 1, NULL, '校内志愿服务与公益活动', '李同学', 1),
(2003, '摄影协会', 1, NULL, '校园影像记录与摄影教学', '王同学', 1);

-- 账号：1 管理员，2 组织者，3/4/5 学生
-- 后台密码：admin / Admin@123      org2001 / Org@123
INSERT INTO sys_user (id, open_id, nickname, role, org_id, username, password_hash, status) VALUES
(1, 'admin:admin',     '系统管理员', 'ADMIN',     NULL, 'admin',   '$2y$10$ek34CjWqpfOfFHKhv3EY3OaDQ12mhrjdmdFU24gT/oBuPlZ76l0ta', 1),
(2, 'mock-organizer-1','计院学生会', 'ORGANIZER', 2001, 'org2001', '$2y$10$XMm30VfQ1k69wbAl2r2n7uCD83hIXnGPt7Ytc5pm5wFaDfI6OJGZq', 1),
(3, 'mock-student-1',  '张小明',    'STUDENT',   NULL, NULL, NULL, 1),
(4, 'mock-student-2',  '李小红',    'STUDENT',   NULL, NULL, NULL, 1),
(5, 'mock-student-3',  '王大力',    'STUDENT',   NULL, NULL, NULL, 1);

-- 学籍白名单
INSERT INTO student_whitelist (id, student_no, real_name, college, grade, major, used_flag) VALUES
(4001, '2023001', '张小明', '计算机学院', '2023', '软件工程', 0),
(4002, '2023002', '李小红', '计算机学院', '2023', '计算机科学与技术', 0),
(4003, '2023003', '王大力', '软件工程学院', '2024', '软件工程', 0),
(4004, '2024001', '赵静',   '软件工程学院', '2024', '人工智能', 0),
(4005, '2024002', '陈晨',   '计算机学院', '2024', '网络工程', 0);

INSERT INTO org_member (id, org_id, user_id, title) VALUES
(5001, 2001, 2, '负责人');

-- 示例活动 1：可报名（报名中，3 天后开始）
INSERT INTO activity (id, title, cover_url, category_id, org_id, creator_id, summary, content,
                      start_time, end_time, signup_start_time, signup_end_time, cancel_deadline,
                      location_name, address, latitude, longitude, capacity, signed_count,
                      waitlist_enabled, audit_required, checkin_type, form_fields, status,
                      view_count, favorite_count, signup_count, publish_time)
VALUES (3001, '春季校园歌手大赛初赛', NULL, 1001, 2001, 2,
        '面向全校学生的歌手大赛初赛，现场乐队伴奏，前三名晋级决赛。',
        '活动流程：13:30 签到入场，14:00 正式开始，每人演唱一首，评委现场打分。\n参赛选手请自备伴奏，提前 20 分钟到场试音。',
        DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 3 HOUR,
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY) - INTERVAL 2 HOUR,
        '大学生活动中心 报告厅', '南京市栖霞区文苑路 1 号', 32.1100000, 118.9200000, 50, 0,
        1, 0, 1,
        JSON_ARRAY(JSON_OBJECT('key', 'song', 'label', '演唱曲目', 'type', 'TEXT', 'required', true)),
        3, 128, 6, 0, NOW()),
       (3002, '周末栖霞山环保志愿行', NULL, 1003, 2002, 2,
        '沿登山步道清理垃圾，宣传无痕山林理念，提供志愿服务时长证明。',
        '集合地点：学校南门。统一乘车前往，活动结束后原路返回。请穿运动鞋，自带水杯。',
        DATE_ADD(NOW(), INTERVAL 6 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY) + INTERVAL 5 HOUR,
        NOW(), DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 6 DAY) - INTERVAL 2 HOUR,
        '栖霞山风景区', '南京市栖霞区栖霞街 88 号', 32.1530000, 118.9570000, 30, 0,
        1, 1, 2,
        NULL, 3, 42, 2, 0, NOW()),
       (3003, '人工智能前沿讲座：大模型与教育', NULL, 1002, 2001, 2,
        '邀请高校与企业专家分享大模型在教育场景的落地实践与工程挑战。',
        '讲座主题包含：大模型基本原理、教育场景应用、工程化部署要点。现场设提问环节。',
        DATE_ADD(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY) + INTERVAL 2 HOUR,
        DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY) - INTERVAL 2 HOUR,
        '图书馆 学术报告厅', '南京市栖霞区文苑路 1 号', 32.1110000, 118.9210000, 120, 0,
        0, 0, 1,
        NULL, 3, 76, 3, 0, NOW()),
       (3004, '迎新晚会节目征集（待审核示例）', NULL, 1001, 2001, 2,
        '面向各班级征集迎新晚会节目，形式不限。',
        '节目时长控制在 5 分钟以内，需提交节目简介与参与人数。',
        DATE_ADD(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY) + INTERVAL 3 HOUR,
        NOW(), DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY) - INTERVAL 2 HOUR,
        '大学生活动中心 礼堂', '南京市栖霞区文苑路 1 号', 32.1100000, 118.9200000, 0, 0,
        0, 0, 1, NULL, 1, 0, 0, 0, NULL);
