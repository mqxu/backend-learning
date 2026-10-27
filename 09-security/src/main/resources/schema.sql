-- 09-security 用的是同一个库 backend_learning，表名统一加 sec_ 前缀，
-- 避免与 05-mybatis 的 t_user 等表冲突，十个模块可以在一个库里共存。
CREATE DATABASE IF NOT EXISTS backend_learning DEFAULT CHARSET utf8mb4;
USE backend_learning;

-- 先删有外键指向的表，再删被指向的表
DROP TABLE IF EXISTS sec_task;
DROP TABLE IF EXISTS sec_user_role;
DROP TABLE IF EXISTS sec_user;
DROP TABLE IF EXISTS sec_role;

-- 角色表：只存角色本身，权限点留在 Role 枚举里，理由见 README 的取舍说明
CREATE TABLE sec_role
(
    id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(20)     NOT NULL COMMENT '角色编码，与 Role 枚举同名：STUDENT / TEACHER / ADMIN',
    name VARCHAR(50)     NOT NULL COMMENT '角色名称，给页面显示',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB COMMENT '角色';

-- 用户表：password 存 BCrypt 密文，定长 60，留 100 以便将来换更长的算法
CREATE TABLE sec_user
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)     NOT NULL COMMENT '登录名',
    password   VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文，不可逆',
    nickname   VARCHAR(50)     NOT NULL COMMENT '姓名，页面上显示',
    enabled    TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用，停用账号登录会被拒',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB COMMENT '账号';

-- 用户角色关联表：一个用户可以有多个角色，主键就是这两列的组合
CREATE TABLE sec_user_role
(
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 id',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB COMMENT '用户与角色的关联';

-- 任务表：归属用 owner_id 指向 sec_user，不冗余存用户名
CREATE TABLE sec_task
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(100)    NOT NULL COMMENT '任务标题',
    content      VARCHAR(2000)   NOT NULL COMMENT '完成情况说明',
    owner_id     BIGINT UNSIGNED NOT NULL COMMENT '提交人，指向 sec_user.id',
    status       VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED 待批改 / REVIEWED 已批改',
    score        TINYINT UNSIGNED         DEFAULT NULL COMMENT '分数 0 到 100，未批改时为空',
    comment      VARCHAR(500)             DEFAULT NULL COMMENT '教师评语',
    submitted_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    reviewed_at  DATETIME                 DEFAULT NULL COMMENT '批改时间',
    PRIMARY KEY (id),
    KEY idx_task_owner (owner_id),
    KEY idx_task_status (status)
) ENGINE = InnoDB COMMENT '实训任务提交记录';
