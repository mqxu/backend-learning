DROP TABLE IF EXISTS t_goods;

CREATE TABLE t_goods
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100)    NOT NULL COMMENT '商品名称',
    category    VARCHAR(32)     NOT NULL COMMENT '分类',
    price       DECIMAL(10, 2)  NOT NULL COMMENT '价格',
    stock       INT             NOT NULL DEFAULT 0 COMMENT '库存',
    sales       INT             NOT NULL DEFAULT 0 COMMENT '销量',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 上架，0 下架',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category (category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品表';
