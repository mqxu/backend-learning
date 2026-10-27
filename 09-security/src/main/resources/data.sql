-- 演示数据。主键写死，测试里引用任务 id 才稳定。
-- 密码是 BCrypt 密文，明文见 README 的账号表；换密码要重新算哈希，不要手改这一列。
USE backend_learning;

INSERT INTO sec_role (id, code, name)
VALUES (1, 'STUDENT', '学生'),
       (2, 'TEACHER', '教师'),
       (3, 'ADMIN', '管理员');

INSERT INTO sec_user (id, username, password, nickname, enabled)
VALUES (1, 'admin', '$2a$10$9s3TX0V3ynwtfUc2eSRKOeQhW5qZbqbELGu7hYP8TGkArhgW0CDFi', '系统管理员', 1),
       (2, 'teacher01', '$2a$10$dMq3yX/ibVFO2DNsAa6iRusGwIXd6vFq8zi2Kij.TeX7D69shvoEu', '陶老师', 1),
       (3, 'student01', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '朱彦清', 1),
       (4, 'student02', '$2a$10$x3ZGTFsyVPnM1rKpCQQGU.VenbXoTmUr/8nf7lpQ6kCVh3lEYVk/W', '李思远', 1),
       (5, 'guest', '$2a$10$D5DZotFstAji9cba0cQTke2W0MWz1wIzDD2n9dYQNNc4DfwRLF88K', '已停用学生', 0);

INSERT INTO sec_user_role (user_id, role_id)
VALUES (1, 3),
       (2, 2),
       (3, 1),
       (4, 1),
       (5, 1);

INSERT INTO sec_task (id, title, content, owner_id, status, score, comment, submitted_at, reviewed_at)
VALUES (1, '实训一：用户登录接口', '登录接口做完了，密码用 BCrypt 加密，返回 JWT。', 3, 'SUBMITTED', NULL, NULL,
        NOW() - INTERVAL 2 HOUR, NULL),
       (2, '实训二：接口鉴权', '教师能批改，学生只能看自己的。', 4, 'REVIEWED', 92,
        '归属判断写对了。令牌过期要返回 401，别返回 200。', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 20 HOUR);
