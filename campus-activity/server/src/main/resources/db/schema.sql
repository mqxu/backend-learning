-- ============================================================
-- 校园活动小程序 V1 数据库结构
-- MySQL 8.0+ / utf8mb4
-- 所有主键使用应用层雪花 ID（BIGINT，非自增），种子数据手工指定
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS audit_record;
DROP TABLE IF EXISTS sys_notice;
DROP TABLE IF EXISTS activity_favorite;
DROP TABLE IF EXISTS activity_checkin;
DROP TABLE IF EXISTS activity_signup;
DROP TABLE IF EXISTS activity;
DROP TABLE IF EXISTS activity_category;
DROP TABLE IF EXISTS org_member;
DROP TABLE IF EXISTS organization;
DROP TABLE IF EXISTS student_whitelist;
DROP TABLE IF EXISTS student_profile;
DROP TABLE IF EXISTS sys_user;

-- 用户：小程序学生、组织者、后台账号共用一张表，用 role 区分
CREATE TABLE sys_user (
    id              BIGINT       NOT NULL                COMMENT '主键，雪花 ID',
    open_id         VARCHAR(64)  NOT NULL                COMMENT '微信 openid，后台账号用 admin:<username> 占位',
    nickname        VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
    avatar_url      VARCHAR(500) DEFAULT NULL            COMMENT '头像',
    phone           VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    role            VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT / ORGANIZER / REVIEWER / ADMIN',
    org_id          BIGINT       DEFAULT NULL            COMMENT '组织者所属组织',
    username        VARCHAR(50)  DEFAULT NULL            COMMENT '后台登录名，仅后台账号使用',
    password_hash   VARCHAR(100) DEFAULT NULL            COMMENT 'BCrypt 密码，仅后台账号使用',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常 0 禁用',
    last_login_time DATETIME     DEFAULT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_id (open_id),
    UNIQUE KEY uk_username (username),
    KEY idx_role_org (role, org_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- 学籍信息：绑定后写入，学号唯一
CREATE TABLE student_profile (
    user_id     BIGINT      NOT NULL COMMENT '用户 ID',
    student_no  VARCHAR(32) NOT NULL COMMENT '学号',
    real_name   VARCHAR(50) NOT NULL COMMENT '姓名',
    college     VARCHAR(60) DEFAULT NULL COMMENT '学院',
    grade       VARCHAR(20) DEFAULT NULL COMMENT '年级，如 2023',
    major       VARCHAR(60) DEFAULT NULL COMMENT '专业',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_student_no (student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学籍信息';

-- 学籍白名单：由管理员 Excel 导入，用于绑定校验
CREATE TABLE student_whitelist (
    id          BIGINT      NOT NULL,
    student_no  VARCHAR(32) NOT NULL,
    real_name   VARCHAR(50) NOT NULL,
    college     VARCHAR(60) DEFAULT NULL,
    grade       VARCHAR(20) DEFAULT NULL,
    major       VARCHAR(60) DEFAULT NULL,
    used_flag   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否已被绑定',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学籍白名单';

-- 组织：社团 / 院系 / 部门
CREATE TABLE organization (
    id          BIGINT       NOT NULL,
    name        VARCHAR(80)  NOT NULL COMMENT '组织名称',
    type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 社团 2 院系 3 部门',
    logo_url    VARCHAR(500) DEFAULT NULL,
    description VARCHAR(300) DEFAULT NULL,
    contact     VARCHAR(50)  DEFAULT NULL COMMENT '联系人',
    status      TINYINT      NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='组织';

CREATE TABLE org_member (
    id          BIGINT      NOT NULL,
    org_id      BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    title       VARCHAR(30) DEFAULT '成员' COMMENT '负责人 / 成员',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_user (org_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='组织成员';

CREATE TABLE activity_category (
    id     BIGINT      NOT NULL,
    name   VARCHAR(30) NOT NULL COMMENT '分类名',
    code   VARCHAR(30) NOT NULL COMMENT '英文编码',
    icon   VARCHAR(50) DEFAULT NULL COMMENT '图标名或颜色值',
    sort   INT         NOT NULL DEFAULT 0,
    status TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='活动分类';

CREATE TABLE activity (
    id                BIGINT       NOT NULL,
    title             VARCHAR(60)  NOT NULL COMMENT '标题',
    cover_url         VARCHAR(500) DEFAULT NULL COMMENT '封面',
    category_id       BIGINT       NOT NULL COMMENT '分类',
    org_id            BIGINT       NOT NULL COMMENT '主办组织',
    creator_id        BIGINT       NOT NULL COMMENT '创建人',
    summary           VARCHAR(200) DEFAULT NULL COMMENT '简介',
    content           TEXT         DEFAULT NULL COMMENT '详情正文',
    start_time        DATETIME     NOT NULL COMMENT '活动开始',
    end_time          DATETIME     NOT NULL COMMENT '活动结束',
    signup_start_time DATETIME     NOT NULL COMMENT '报名开始',
    signup_end_time   DATETIME     NOT NULL COMMENT '报名结束',
    cancel_deadline   DATETIME     DEFAULT NULL COMMENT '取消报名截止',
    location_name     VARCHAR(120) NOT NULL COMMENT '场地名称',
    address           VARCHAR(200) DEFAULT NULL COMMENT '详细地址',
    latitude          DECIMAL(10, 7) DEFAULT NULL,
    longitude         DECIMAL(10, 7) DEFAULT NULL,
    capacity          INT          NOT NULL DEFAULT 0 COMMENT '容量，0 表示不限',
    signed_count      INT          NOT NULL DEFAULT 0 COMMENT '已占名额',
    waitlist_enabled  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否开启候补',
    audit_required    TINYINT      NOT NULL DEFAULT 0 COMMENT '报名是否需审核',
    checkin_type      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 扫码 2 扫码+定位 3 仅手动',
    form_fields       JSON         DEFAULT NULL COMMENT '自定义报名字段定义',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0 草稿 1 待审核 2 已驳回 3 已发布 4 进行中 5 已结束 6 已取消',
    reject_reason     VARCHAR(300) DEFAULT NULL,
    view_count        INT          NOT NULL DEFAULT 0,
    favorite_count    INT          NOT NULL DEFAULT 0,
    signup_count      INT          NOT NULL DEFAULT 0 COMMENT '累计报名数（含取消）',
    publish_time      DATETIME     DEFAULT NULL,
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_start (status, start_time),
    KEY idx_org (org_id),
    KEY idx_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='活动';

CREATE TABLE activity_signup (
    id            BIGINT      NOT NULL,
    activity_id   BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    signup_no     VARCHAR(32) NOT NULL COMMENT '签到凭证号',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '0 待审核 1 报名成功 2 候补中 3 已签到 4 已取消 5 审核未通过 6 未到场',
    waitlist_seq  INT         NOT NULL DEFAULT 0,
    form_data     JSON        DEFAULT NULL,
    audit_user_id BIGINT      DEFAULT NULL,
    audit_time    DATETIME    DEFAULT NULL,
    cancel_time   DATETIME    DEFAULT NULL,
    checkin_time  DATETIME    DEFAULT NULL,
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_user (activity_id, user_id),
    UNIQUE KEY uk_signup_no (signup_no),
    KEY idx_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='报名记录';

CREATE TABLE activity_checkin (
    id           BIGINT   NOT NULL,
    activity_id  BIGINT   NOT NULL,
    signup_id    BIGINT   NOT NULL,
    user_id      BIGINT   NOT NULL,
    checkin_type TINYINT  NOT NULL DEFAULT 1 COMMENT '1 扫码 2 定位 3 补签',
    operator_id  BIGINT   DEFAULT NULL COMMENT '补签操作人',
    latitude     DECIMAL(10, 7) DEFAULT NULL,
    longitude    DECIMAL(10, 7) DEFAULT NULL,
    remark       VARCHAR(200) DEFAULT NULL,
    checkin_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_signup (signup_id),
    KEY idx_activity (activity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='签到记录';

CREATE TABLE activity_favorite (
    id          BIGINT   NOT NULL,
    activity_id BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_user (activity_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='收藏';

CREATE TABLE sys_notice (
    id          BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(30)  NOT NULL COMMENT 'SIGNUP / AUDIT / REMIND / CANCEL / WAITLIST',
    title       VARCHAR(80)  NOT NULL,
    content     VARCHAR(500) DEFAULT NULL,
    biz_id      BIGINT       DEFAULT NULL COMMENT '关联业务 ID',
    read_flag   TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_read (user_id, read_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='站内消息';

CREATE TABLE audit_record (
    id            BIGINT       NOT NULL,
    biz_type      VARCHAR(30)  NOT NULL COMMENT 'ACTIVITY / SIGNUP / CHECKIN / USER',
    biz_id        BIGINT       NOT NULL,
    action        VARCHAR(30)  NOT NULL COMMENT 'APPROVE / REJECT / CANCEL / MANUAL_CHECKIN / ROLE_CHANGE',
    operator_id   BIGINT       NOT NULL,
    operator_name VARCHAR(50)  DEFAULT NULL,
    before_status VARCHAR(30)  DEFAULT NULL,
    after_status  VARCHAR(30)  DEFAULT NULL,
    remark        VARCHAR(300) DEFAULT NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审计记录';

SET FOREIGN_KEY_CHECKS = 1;
