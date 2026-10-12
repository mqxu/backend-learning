# 创建数据库，如果不存在的话
CREATE DATABASE IF NOT EXISTS backend_learning DEFAULT CHARSET utf8mb4;
# 使用该数据库
USE backend_learning;

-- 用户表：覆盖 MySQL 常用数据类型，字段对应用户数据的常见形态
-- 配合 spring.sql.init.mode=always 在应用启动时执行
-- 先删后建：改字段时不用手工 ALTER，保证表结构始终与实体类一致；真实项目请用 Flyway / Liquibase 做版本化迁移
DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user
(
    id              BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    username        VARCHAR(50)  NOT NULL COMMENT '登录名',
    password        CHAR(64)     NOT NULL COMMENT '密码，SHA-256 摘要定长 64 位',
    nickname        VARCHAR(50) COMMENT '昵称',
    gender          TINYINT      NOT NULL DEFAULT 0 COMMENT '性别，0 未设置 / 1 男 / 2 女',
    age             TINYINT UNSIGNED COMMENT '年龄',
    member_level    ENUM ('BRONZE','SILVER','GOLD') NOT NULL DEFAULT 'BRONZE' COMMENT '会员等级',
    points          INT          NOT NULL DEFAULT 0 COMMENT '积分',
    balance         DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额，金额用定点数',
    score           FLOAT COMMENT '推荐评分，近似小数用浮点',
    phone           VARCHAR(20) COMMENT '手机号',
    email           VARCHAR(100) COMMENT '邮箱',
    avatar          VARCHAR(255) COMMENT '头像地址',
    intro           TEXT COMMENT '个人简介，长文本',
    tags            JSON COMMENT '标签数组，如 ["Java","篮球"]',
    birthday        DATE COMMENT '生日',
    work_time       TIME COMMENT '每日上班时间',
    vip             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否会员，0 否 / 1 是',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态，1 正常 / 0 禁用',
    last_login_time DATETIME COMMENT '最后登录时间',
    create_time     DATETIME     NOT NULL COMMENT '创建时间，MyBatis-Plus 自动填充',
    update_time     DATETIME     NOT NULL COMMENT '更新时间，MyBatis-Plus 自动填充',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status_gender (status, gender)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- 课程表：课后进阶练习用，字段刻意保持简单，只用来演练 saveBatch 与 removeByIds
-- 这里没有审计字段，正好验证自动填充器对没有该属性的实体不会报错
DROP TABLE IF EXISTS t_course;

CREATE TABLE t_course
(
    id      BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    name    VARCHAR(64)      NOT NULL COMMENT '课程名',
    teacher VARCHAR(32) COMMENT '授课教师',
    credit  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '学分',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程表';

-- 商品表：挑战练习里扣库存的对象
DROP TABLE IF EXISTS t_product;

CREATE TABLE t_product
(
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)     NOT NULL COMMENT '商品名',
    price       DECIMAL(10, 2)  NOT NULL DEFAULT 0.00 COMMENT '单价',
    stock       INT             NOT NULL DEFAULT 0 COMMENT '库存',
    create_time DATETIME        NOT NULL COMMENT '创建时间，自动填充',
    update_time DATETIME        NOT NULL COMMENT '更新时间，自动填充',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品表';

-- 订单表：挑战练习里下单的结果，带自动填充与逻辑删除
DROP TABLE IF EXISTS t_order;

CREATE TABLE t_order
(
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    order_no    VARCHAR(32)     NOT NULL COMMENT '订单号',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '下单用户',
    product_id  BIGINT UNSIGNED NOT NULL COMMENT '商品',
    quantity    INT             NOT NULL DEFAULT 1 COMMENT '购买数量',
    amount      DECIMAL(12, 2)  NOT NULL DEFAULT 0.00 COMMENT '订单金额 = 单价 * 数量',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态，1 已创建 / 0 已取消',
    create_time DATETIME        NOT NULL COMMENT '创建时间，自动填充',
    update_time DATETIME        NOT NULL COMMENT '更新时间，自动填充',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单表';
