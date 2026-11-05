-- ============================================================
-- 知乎案例：问答社区核心表结构
-- MySQL 8.0+ / utf8mb4
-- 执行顺序：schema.sql → data.sql
--
-- 表名统一 z_ 前缀（zhihu），与 backend_learning 里 01-14 的表互不干扰。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS z_vote;
DROP TABLE IF EXISTS z_follow;
DROP TABLE IF EXISTS z_comment;
DROP TABLE IF EXISTS z_answer;
DROP TABLE IF EXISTS z_question;
DROP TABLE IF EXISTS z_topic;
DROP TABLE IF EXISTS z_user;
DROP TABLE IF EXISTS z_special;

SET FOREIGN_KEY_CHECKS = 1;

-- 用户：密码存 BCrypt 密文，长度 100 是为了将来换更长的算法
CREATE TABLE z_user
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)  NOT NULL COMMENT '登录名',
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname   VARCHAR(50)  NOT NULL COMMENT '昵称，页面显示',
    avatar     VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    bio        VARCHAR(255)          DEFAULT NULL COMMENT '一句话简介',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB COMMENT '用户';

-- 话题：问题的分类，一个话题下多个问题
CREATE TABLE z_topic
(
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(50) NOT NULL COMMENT '话题名',
    description      VARCHAR(255)         DEFAULT NULL COMMENT '话题说明',
    followers_count  INT         NOT NULL DEFAULT 0 COMMENT '关注人数',
    question_count   INT         NOT NULL DEFAULT 0 COMMENT '问题数',
    sort             INT         NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_name (name)
) ENGINE = InnoDB COMMENT '话题';

-- 问题：answer_count 等计数列是冗余字段，避免列表页每行都 COUNT 一次
CREATE TABLE z_question
(
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title        VARCHAR(200)  NOT NULL COMMENT '标题',
    content      VARCHAR(5000) NOT NULL COMMENT '补充说明',
    author_id    BIGINT        NOT NULL COMMENT '提问人',
    topic_id     BIGINT        NOT NULL COMMENT '所属话题',
    answer_count INT           NOT NULL DEFAULT 0 COMMENT '回答数（冗余计数）',
    view_count   INT           NOT NULL DEFAULT 0 COMMENT '浏览数',
    vote_count   INT           NOT NULL DEFAULT 0 COMMENT '问题被赞数',
    status       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 正常 / 1 已关闭',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (id),
    KEY idx_question_topic (topic_id),
    KEY idx_question_author (author_id),
    KEY idx_question_created (created_at)
) ENGINE = InnoDB COMMENT '问题';

-- 回答：vote_up / vote_down 分开存，方便展示"赞同 128 · 反对 3"
CREATE TABLE z_answer
(
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_id   BIGINT        NOT NULL COMMENT '所属问题',
    author_id     BIGINT        NOT NULL COMMENT '回答人',
    content       VARCHAR(5000) NOT NULL COMMENT '回答正文',
    vote_up       INT           NOT NULL DEFAULT 0 COMMENT '赞同数',
    vote_down     INT           NOT NULL DEFAULT 0 COMMENT '反对数',
    comment_count INT           NOT NULL DEFAULT 0 COMMENT '评论数（冗余计数）',
    accepted      TINYINT       NOT NULL DEFAULT 0 COMMENT '1 表示被提问者采纳',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回答时间',
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_answer_question (question_id),
    KEY idx_answer_author (author_id)
) ENGINE = InnoDB COMMENT '回答';

-- 评论：parent_id 支持一层楼中楼，reply_to_user_id 记录"回复谁"
CREATE TABLE z_comment
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    answer_id        BIGINT       NOT NULL COMMENT '所属回答',
    author_id        BIGINT       NOT NULL COMMENT '评论人',
    content          VARCHAR(500) NOT NULL COMMENT '评论内容',
    parent_id        BIGINT       NOT NULL DEFAULT 0 COMMENT '父评论 id，0 表示一级评论',
    reply_to_user_id BIGINT                DEFAULT NULL COMMENT '回复的目标用户',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_comment_answer (answer_id)
) ENGINE = InnoDB COMMENT '评论';

-- 投票：一个人对同一个目标只能有一票，唯一键兜住并发重复插入
CREATE TABLE z_vote
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL COMMENT '投票人',
    target_type VARCHAR(10) NOT NULL COMMENT 'ANSWER / QUESTION',
    target_id   BIGINT      NOT NULL COMMENT '目标 id',
    value       TINYINT     NOT NULL COMMENT '1 赞同 / -1 反对',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vote (user_id, target_type, target_id),
    KEY idx_vote_target (target_type, target_id)
) ENGINE = InnoDB COMMENT '投票';

-- 关注：用户关注人、问题、话题都用这一张表
CREATE TABLE z_follow
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL COMMENT '关注人',
    target_type VARCHAR(10) NOT NULL COMMENT 'USER / QUESTION / TOPIC',
    target_id   BIGINT      NOT NULL COMMENT '被关注对象 id',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (user_id, target_type, target_id),
    KEY idx_follow_target (target_type, target_id)
) ENGINE = InnoDB COMMENT '关注关系';

-- 专栏：保留原案例的表结构，前端仍在用
CREATE TABLE z_special
(
    id              BIGINT       NOT NULL COMMENT '主键',
    title           VARCHAR(255)          DEFAULT NULL COMMENT '标题',
    introduction    VARCHAR(10000)        DEFAULT NULL COMMENT '简介',
    banner          VARCHAR(500)          DEFAULT NULL COMMENT '封面图',
    view_count      INT                   DEFAULT NULL COMMENT '浏览数',
    followers_count INT                   DEFAULT NULL COMMENT '关注数',
    is_following    VARCHAR(255)          DEFAULT NULL COMMENT '是否已关注，前端直接读这个字段',
    updated         BIGINT                DEFAULT NULL COMMENT '更新时间戳（秒）',
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '专栏';
