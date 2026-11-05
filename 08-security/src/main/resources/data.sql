-- 六个账号，各自负责一个演示场景。密码都是 123456，存的是 BCrypt 密文
INSERT INTO sec_user (id, username, phone, password, nickname, enabled, locked)
VALUES (1, 'alice', '13800000001', '{bcrypt}$2a$10$C4766jheAQ0QhL3Jslfq2uyXheKoxcnXkS6lemAhqJEY40a7pLvdW', '爱丽丝', TRUE, FALSE),
       (2, 'bob', '13800000002', '{bcrypt}$2a$10$C4766jheAQ0QhL3Jslfq2uyXheKoxcnXkS6lemAhqJEY40a7pLvdW', '鲍勃', TRUE, FALSE),
       (3, 'carol', '13800000003', '{bcrypt}$2a$10$C4766jheAQ0QhL3Jslfq2uyXheKoxcnXkS6lemAhqJEY40a7pLvdW', '卡罗尔', TRUE, FALSE),
       (4, 'dave', NULL, '{bcrypt}$2a$10$C4766jheAQ0QhL3Jslfq2uyXheKoxcnXkS6lemAhqJEY40a7pLvdW', '戴夫', FALSE, FALSE),
       (5, 'frank', NULL, '{bcrypt}$2a$10$C4766jheAQ0QhL3Jslfq2uyXheKoxcnXkS6lemAhqJEY40a7pLvdW', '弗兰克', TRUE, TRUE),
       (6, 'erin', '13800000006', '{bcrypt}$2a$10$C4766jheAQ0QhL3Jslfq2uyXheKoxcnXkS6lemAhqJEY40a7pLvdW', '艾琳', TRUE, FALSE);

-- alice: 普通用户 + 一个业务权限点，用来演示 hasAuthority
-- bob: ADMIN + AUDITOR，用来演示 hasAllRoles 全部具备
-- carol: 只有 ADMIN，hasAllRoles 会拒
-- erin: 只有 AUDITOR，用来演示 hasAnyRole
INSERT INTO sec_role (id, user_id, role_code, grant_type)
VALUES (1, 1, 'USER', 'ROLE'),
       (2, 1, 'ARTICLE_DELETE', 'PERM'),
       (3, 2, 'ADMIN', 'ROLE'),
       (4, 2, 'AUDITOR', 'ROLE'),
       (5, 3, 'ADMIN', 'ROLE'),
       (6, 4, 'USER', 'ROLE'),
       (7, 5, 'USER', 'ROLE'),
       (8, 6, 'AUDITOR', 'ROLE');
