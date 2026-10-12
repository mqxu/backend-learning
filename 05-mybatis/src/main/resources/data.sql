-- 初始化数据：覆盖不同性别、等级、状态、逻辑删除、会员与各类字段的典型取值
-- password 字段统一存 SHA-256("123456") 的十六进制摘要
-- 表由 schema.sql 先删后建，这里直接 INSERT 即可，重复启动不会累积脏数据
INSERT INTO t_user (id, username, password, nickname, gender, age, member_level, points, balance, score,
                    phone, email, avatar, intro, tags, birthday, work_time, vip, status,
                    last_login_time, create_time, update_time, deleted, version)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '超级管理员',
        1, 35, 'GOLD', 12000, 99999.99, 4.9, '13800000001', 'admin@mqxu.top', 'https://cdn.mqxu.top/a/admin.png',
        '系统内置账号，拥有全部权限。', '["管理员","全栈开发","技术负责人"]', '1991-05-20', '09:00:00', 1, 1,
        '2026-10-09 08:30:00', '2026-09-01 10:00:00', '2026-10-09 08:30:00', 0, 0),

       (2, 'zhangsan', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '张三',
        1, 20, 'BRONZE', 120, 88.50, 4.2, '13800000002', 'zhangsan@mqxu.top', 'https://cdn.mqxu.top/a/zhangsan.png',
        '在读大二，正在学 Spring Boot 与 MyBatis-Plus。', '["学生","Java","篮球"]', '2006-03-15', '08:30:00', 0, 1,
        '2026-10-08 21:12:00', '2026-09-05 09:20:00', '2026-10-08 21:12:00', 0, 0),

       (3, 'lisi', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '李四',
        2, 26, 'SILVER', 2600, 1280.00, 4.5, '13800000003', 'lisi@mqxu.top', 'https://cdn.mqxu.top/a/lisi.png',
        '前端工程师，业余写 Vue 组件库。', '["前端","Vue","摄影"]', '2000-11-02', '09:30:00', 1, 1,
        '2026-10-09 19:40:00', '2026-09-10 14:05:00', '2026-10-09 19:40:00', 0, 0),

       (4, 'wangwu', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '王五',
        1, 31, 'GOLD', 8800, 4300.75, 4.8, NULL, 'wangwu@mqxu.top', NULL,
        '后端架构师，关注高并发与数据库性能。', '["架构","MySQL","跑步"]', '1995-07-08', '08:00:00', 1, 1,
        '2026-10-07 07:55:00', '2026-09-12 11:30:00', '2026-10-07 07:55:00', 0, 0),

       (5, 'zhaoliu', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '赵六',
        0, 24, 'BRONZE', 300, 12.30, 3.6, '13800000005', 'zhaoliu@mqxu.top', NULL,
        '这条记录 deleted=1，用来演示逻辑删除查不到。', '["测试"]', '2002-01-19', '10:00:00', 0, 1,
        '2026-09-28 16:20:00', '2026-09-15 09:00:00', '2026-09-28 16:20:00', 1, 0),

       (6, 'sunqi', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '孙七',
        2, 22, 'SILVER', 1500, 640.00, 4.0, '13800000006', 'sunqi@mqxu.top', NULL,
        '这条记录 status=0，用来演示按状态过滤。', '["测试","产品"]', '2004-09-30', '09:15:00', 0, 0,
        '2026-10-01 12:00:00', '2026-09-18 10:45:00', '2026-10-01 12:00:00', 0, 0);

-- 课程：课后进阶练习 saveBatch / removeByIds 的起始数据
INSERT INTO t_course (id, name, teacher, credit)
VALUES (1, '后端工程化开发', '陶老师', 4),
       (2, '前端工程化开发', '陶老师', 3),
       (3, 'Web 应用系统综合实训', '陶老师', 3);

-- 商品：挑战练习里扣库存的对象，库存故意留小，方便一次请求就复现超卖
INSERT INTO t_product (id, name, price, stock, create_time, update_time, deleted, version)
VALUES (1, '机械键盘', 399.00, 10, '2026-10-01 10:00:00', '2026-10-01 10:00:00', 0, 0),
       (2, '人体工学椅', 1299.00, 3, '2026-10-01 10:00:00', '2026-10-01 10:00:00', 0, 0);
