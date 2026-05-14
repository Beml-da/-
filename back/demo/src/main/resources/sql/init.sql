-- ============================================
-- 校园交易平台 - 数据库初始化脚本 (完整版)
-- 对应前端: jiaoyifront (Vue3 + TypeScript)
-- 对应后端: Spring Boot + MyBatis
-- 数据库: MySQL 8.0+
-- ============================================

CREATE DATABASE IF NOT EXISTS jiaoyihang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jiaoyihang;

-- ============================================
-- 1. 用户表 (sys_user)
-- 对应前端: 登录注册、个人中心
-- ============================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '用户ID',
    username        VARCHAR(50)     NOT NULL                    COMMENT '用户名(登录账号)',
    password        VARCHAR(255)    NOT NULL                    COMMENT '密码(BCrypt加密)',
    nickname        VARCHAR(50)     DEFAULT NULL                COMMENT '昵称',
    avatar          VARCHAR(500)    DEFAULT NULL                COMMENT '头像URL',
    phone           VARCHAR(20)     DEFAULT NULL                COMMENT '手机号',
    email           VARCHAR(100)    DEFAULT NULL                COMMENT '邮箱',
    school          VARCHAR(100)    DEFAULT NULL                COMMENT '学校',
    student_id      VARCHAR(30)     DEFAULT NULL                COMMENT '学号',
    credit_score    INT             DEFAULT 80                   COMMENT '信用分(0-100)',
    level           INT             DEFAULT 1                   COMMENT '用户等级',
    balance         DECIMAL(12,2)   DEFAULT 1000.00             COMMENT '账户余额(元)',
    is_verified     TINYINT(1)      DEFAULT 0                   COMMENT '是否认证: 0=否, 1=是',
    status          TINYINT(1)      DEFAULT 1                   COMMENT '状态: 1=正常, 0=禁用',
    deleted         TINYINT(1)      DEFAULT 0                   COMMENT '删除标记: 0=未删除, 1=已删除',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_phone (phone),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 2. 商品分类表 (sys_category)
-- 对应前端: 分类导航、商品筛选
-- ============================================
DROP TABLE IF EXISTS sys_category;
CREATE TABLE sys_category (
    id          VARCHAR(30)     NOT NULL                COMMENT '分类ID(如: books, electronics)',
    name        VARCHAR(50)     NOT NULL                COMMENT '分类名称',
    icon        VARCHAR(50)     DEFAULT NULL            COMMENT '图标类型',
    color       VARCHAR(20)     DEFAULT NULL            COMMENT '主题色',
    parent_id   VARCHAR(30)     DEFAULT '0'             COMMENT '父分类ID',
    sort_order  INT             DEFAULT 0               COMMENT '排序',
    is_service  TINYINT(1)      DEFAULT 0               COMMENT '是否为服务类型: 0=商品, 1=服务',
    status      TINYINT(1)      DEFAULT 1               COMMENT '状态: 1=正常, 0=禁用',
    deleted     TINYINT(1)      DEFAULT 0               COMMENT '删除标记',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================
-- 3. 商品表 (sys_product) - 同时存储商品和服务
-- 对应前端: 商品列表、商品详情、发布商品、发布服务
-- ============================================
DROP TABLE IF EXISTS sys_product;
CREATE TABLE sys_product (
    id              BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '商品ID',
    title           VARCHAR(200)    NOT NULL                    COMMENT '商品标题/服务标题',
    description     TEXT            DEFAULT NULL                COMMENT '商品描述/服务描述',
    price           DECIMAL(10,2)  NOT NULL                    COMMENT '出售价格/服务价格',
    original_price  DECIMAL(10,2)   DEFAULT NULL                COMMENT '原价',
    images          JSON            DEFAULT NULL                COMMENT '图片URL列表(JSON数组)',
    category_id     VARCHAR(30)     DEFAULT NULL                COMMENT '分类ID(商品用)',
    sub_category    VARCHAR(50)     DEFAULT NULL                COMMENT '子分类(商品用)',
    `condition`     VARCHAR(20)     DEFAULT NULL                COMMENT '商品成色(服务不用)',
    status          VARCHAR(20)     DEFAULT '在售'              COMMENT '商品状态/服务状态',
    view_count      INT             DEFAULT 0                  COMMENT '浏览次数',
    favorite_count  INT             DEFAULT 0                   COMMENT '收藏次数',
    seller_id       BIGINT(20)      NOT NULL                    COMMENT '卖家ID/服务提供者ID',
    location        VARCHAR(200)    DEFAULT NULL                COMMENT '交易地点/服务范围',
    is_negotiable   TINYINT(1)      DEFAULT 1                   COMMENT '是否可议价',
    tags            JSON            DEFAULT NULL                COMMENT '标签列表(JSON数组)',
    deleted         TINYINT(1)      DEFAULT 0                   COMMENT '删除标记',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sold_time       DATETIME        DEFAULT NULL                COMMENT '售出时间',
    type            VARCHAR(20)     NOT NULL                    COMMENT '类型: product=商品, service=服务',
    service_type    VARCHAR(50)     DEFAULT NULL                COMMENT '服务类型(服务用)',
    price_unit      VARCHAR(20)     DEFAULT NULL                COMMENT '计价单位(服务用，如/次 /小时)',
    PRIMARY KEY (id),
    KEY idx_seller_id (seller_id),
    KEY idx_category_id (category_id),
    KEY idx_status (status),
    KEY idx_condition (`condition`),
    KEY idx_create_time (create_time),
    KEY idx_type (type),
    FULLTEXT INDEX ft_title_desc (title, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表(同时存储服务)';

-- ============================================
-- 5. 订单表 (trade_order)
-- 对应前端: 我的订单、订单详情、创建订单
-- ============================================
DROP TABLE IF EXISTS trade_order;
CREATE TABLE trade_order (
    id              BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '订单ID',
    order_no        VARCHAR(32)     NOT NULL                    COMMENT '订单号',
    type            VARCHAR(20)     NOT NULL                    COMMENT '订单类型: 商品/服务',
    product_id      BIGINT(20)      DEFAULT NULL                COMMENT '商品ID(商品订单)',
    service_id      BIGINT(20)      DEFAULT NULL                COMMENT '服务ID(服务订单)',
    buyer_id        BIGINT(20)      NOT NULL                    COMMENT '买家ID',
    seller_id       BIGINT(20)      NOT NULL                    COMMENT '卖家ID',
    price           DECIMAL(10,2)  NOT NULL                    COMMENT '单价',
    quantity        INT             DEFAULT 1                   COMMENT '数量',
    total_amount    DECIMAL(10,2)  NOT NULL                    COMMENT '总金额',
    status          VARCHAR(20)     DEFAULT '待付款'              COMMENT '订单状态(待付款/待发货/待收货/已完成/已取消)',
    contact         VARCHAR(50)     DEFAULT NULL                COMMENT '联系方式',
    remark          TEXT            DEFAULT NULL                COMMENT '买家备注',
    deleted         TINYINT(1)      DEFAULT 0                   COMMENT '删除标记',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    paid_time       DATETIME        DEFAULT NULL                COMMENT '付款时间',
    shipped_time    DATETIME        DEFAULT NULL                COMMENT '发货时间',
    completed_time  DATETIME        DEFAULT NULL                COMMENT '完成时间',
    canceled_time   DATETIME        DEFAULT NULL                COMMENT '取消时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_buyer_id (buyer_id),
    KEY idx_seller_id (seller_id),
    KEY idx_status (status),
    KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================
-- 6. 收藏表 (sys_favorite)
-- 对应前端: 我的收藏、收藏/取消收藏
-- ============================================
DROP TABLE IF EXISTS sys_favorite;
CREATE TABLE sys_favorite (
    id          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    user_id     BIGINT(20)  NOT NULL                COMMENT '用户ID',
    product_id  BIGINT(20)  NOT NULL                COMMENT '商品ID',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user_id (user_id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- ============================================
-- 7. 聊天会话表 (chat_session)
-- 对应前端: 消息列表、聊天页面
-- ============================================
DROP TABLE IF EXISTS chat_session;
CREATE TABLE chat_session (
    id              BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    user_id         BIGINT(20)  NOT NULL                COMMENT '用户ID(发起方)',
    target_user_id  BIGINT(20)  NOT NULL                COMMENT '目标用户ID',
    last_message_id BIGINT(20)  DEFAULT NULL            COMMENT '最后消息ID',
    unread_count    INT         DEFAULT 0              COMMENT '未读消息数',
    create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users (user_id, target_user_id),
    KEY idx_target_user_id (target_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- ============================================
-- 8. 消息表 (chat_message)
-- 对应前端: 聊天消息、发送消息
-- ============================================
DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
    id              BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    session_id      BIGINT(20)  NOT NULL                COMMENT '会话ID',
    type            VARCHAR(20) DEFAULT 'chat'           COMMENT '消息类型: chat/system/order',
    from_id         BIGINT(20)  NOT NULL                COMMENT '发送者ID',
    to_id           BIGINT(20)  NOT NULL                COMMENT '接收者ID',
    content         TEXT        NOT NULL                COMMENT '消息内容',
    is_read         TINYINT(1)  DEFAULT 0                COMMENT '是否已读: 0=未读, 1=已读',
    related_type    VARCHAR(20) DEFAULT NULL            COMMENT '关联类型: product/service/order',
    related_id      BIGINT(20)  DEFAULT NULL            COMMENT '关联ID',
    create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_from_id (from_id),
    KEY idx_to_id (to_id),
    KEY idx_is_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- ============================================
-- 9. 通知表 (sys_notification)
-- 对应前端: 消息通知、通知详情
-- ============================================
DROP TABLE IF EXISTS sys_notification;
CREATE TABLE sys_notification (
    id          BIGINT(20)      NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    user_id     BIGINT(20)      NOT NULL                COMMENT '用户ID',
    type        VARCHAR(20)     NOT NULL                COMMENT '通知类型: order/system/activity',
    title       VARCHAR(200)    NOT NULL                COMMENT '通知标题',
    content     TEXT            DEFAULT NULL            COMMENT '通知内容',
    is_read     TINYINT(1)      DEFAULT 0               COMMENT '是否已读: 0=未读, 1=已读',
    link        VARCHAR(255)    DEFAULT NULL            COMMENT '跳转链接',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_is_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

-- ============================================
-- 10. 评价表 (sys_review)
-- 对应前端: 订单评价、评价详情
-- ============================================
DROP TABLE IF EXISTS sys_review;
CREATE TABLE sys_review (
    id          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '评价ID',
    order_id    BIGINT(20)  NOT NULL                COMMENT '订单ID',
    reviewer_id BIGINT(20)  NOT NULL                COMMENT '评价者ID',
    reviewee_id BIGINT(20)  NOT NULL                COMMENT '被评价者ID',
    rating      INT         NOT NULL                COMMENT '评分(1-5)',
    content     TEXT        DEFAULT NULL            COMMENT '评价内容',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_reviewer (order_id, reviewer_id),
    KEY idx_reviewee_id (reviewee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- ============================================
-- 11. 收货地址表 (ums_address) 【新增】
-- 对应前端: 订单结算页选择地址
-- ============================================
DROP TABLE IF EXISTS ums_address;
CREATE TABLE ums_address (
    id          BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '地址ID',
    user_id     BIGINT(20)      NOT NULL                    COMMENT '用户ID',
    consignee   VARCHAR(50)     NOT NULL                    COMMENT '收货人姓名',
    phone       VARCHAR(20)     NOT NULL                    COMMENT '手机号',
    province    VARCHAR(50)     NOT NULL                    COMMENT '省份',
    city        VARCHAR(50)     NOT NULL                    COMMENT '城市',
    district    VARCHAR(50)     NOT NULL                    COMMENT '区县',
    street      VARCHAR(255)    DEFAULT NULL                COMMENT '详细地址',
    post_code   VARCHAR(20)     DEFAULT NULL                COMMENT '邮编',
    is_default  TINYINT(1)      DEFAULT 0                   COMMENT '是否默认: 1=是, 0=否',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time DATETIME        DEFAULT CURRENT_TIMESTAMP   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- ============================================
-- 12. 购物车表 (trade_cart) 【新增】
-- 对应前端: 购物车页面、加购商品
-- ============================================
DROP TABLE IF EXISTS trade_cart;
CREATE TABLE trade_cart (
    id          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
    user_id     BIGINT(20)  NOT NULL                COMMENT '用户ID',
    product_id  BIGINT(20)  NOT NULL                COMMENT '商品ID',
    quantity    INT         NOT NULL DEFAULT 1       COMMENT '购买数量',
    is_selected TINYINT(1) DEFAULT 1                COMMENT '是否选中: 1=选中, 0=未选',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_product_id (product_id),
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- ============================================
-- 13. 优惠券表 (promo_coupon) 【新增】
-- 对应前端: 优惠券领取、优惠券使用
-- ============================================
DROP TABLE IF EXISTS promo_coupon;
CREATE TABLE promo_coupon (
    id              BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '优惠券ID',
    name            VARCHAR(100)    NOT NULL                    COMMENT '优惠券名称',
    type            TINYINT          NOT NULL DEFAULT 1          COMMENT '类型: 1=满减券, 2=折扣券, 3=无门槛券',
    condition_amount DECIMAL(10,2) DEFAULT 0.00               COMMENT '使用条件(满X元)',
    reduce_amount   DECIMAL(10,2)   DEFAULT 0.00               COMMENT '满减金额',
    discount        DECIMAL(5,2)    DEFAULT 1.00               COMMENT '折扣率(如0.8表示8折)',
    total_stock     INT             NOT NULL                    COMMENT '发放总量',
    remain_stock    INT             NOT NULL                    COMMENT '剩余数量',
    per_limit       INT             DEFAULT 1                   COMMENT '每人限领数量',
    start_time      DATETIME        DEFAULT NULL                COMMENT '领取开始时间',
    end_time        DATETIME        DEFAULT NULL                COMMENT '领取结束时间',
    valid_days      INT             DEFAULT 7                   COMMENT '领取后有效期(天)',
    status          TINYINT          NOT NULL DEFAULT 1          COMMENT '状态: 1=正常, 0=停用',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_time_range (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

-- ============================================
-- 14. 用户优惠券表 (promo_user_coupon) 【新增】
-- 对应前端: 我的优惠券
-- ============================================
DROP TABLE IF EXISTS promo_user_coupon;
CREATE TABLE promo_user_coupon (
    id              BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '用户优惠券ID',
    user_id         BIGINT(20)  NOT NULL                COMMENT '用户ID',
    coupon_id       BIGINT(20)  NOT NULL                COMMENT '优惠券ID',
    order_id        BIGINT(20)  DEFAULT NULL            COMMENT '关联订单ID(已使用时)',
    status          TINYINT      NOT NULL DEFAULT 0       COMMENT '状态: 0=未使用, 1=已使用, 2=已过期',
    receive_time    DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    used_time       DATETIME    DEFAULT NULL            COMMENT '使用时间',
    expire_time     DATETIME    DEFAULT NULL            COMMENT '过期时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_coupon_id (coupon_id),
    KEY idx_status (status),
    UNIQUE KEY uk_user_coupon (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- ============================================
-- 15. 优惠券使用记录表 (promo_coupon_record) 【新增】
-- 对应前端: 优惠券使用明细
-- ============================================
DROP TABLE IF EXISTS promo_coupon_record;
CREATE TABLE promo_coupon_record (
    id              BIGINT(20)      NOT NULL AUTO_INCREMENT COMMENT '使用记录ID',
    user_id         BIGINT(20)      NOT NULL                COMMENT '用户ID',
    coupon_id       BIGINT(20)      NOT NULL                COMMENT '优惠券ID',
    order_id        BIGINT(20)      NOT NULL                COMMENT '订单ID',
    order_amount    DECIMAL(10,2)  NOT NULL                COMMENT '订单金额',
    discount_amount DECIMAL(10,2)  NOT NULL                COMMENT '优惠金额',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '使用时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券使用记录表';

-- ============================================
-- 16. 搜索历史表 (sys_search_history) 【新增】
-- 对应前端: 搜索页面搜索历史
-- ============================================
DROP TABLE IF EXISTS sys_search_history;
CREATE TABLE sys_search_history (
    id          BIGINT(20)  NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    user_id     BIGINT(20)  NOT NULL                COMMENT '用户ID',
    keyword     VARCHAR(100) NOT NULL               COMMENT '搜索关键词',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

-- ============================================
-- 17. 文件上传记录表 (sys_file) 【新增】
-- 对应前端: 图片上传(商品图片、头像等)
-- ============================================
DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file (
    id          BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '文件ID',
    file_name   VARCHAR(255)    NOT NULL                    COMMENT '原始文件名',
    file_path   VARCHAR(500)    NOT NULL                    COMMENT '存储路径',
    file_url    VARCHAR(500)    NOT NULL                    COMMENT '访问URL',
    file_size   BIGINT          NOT NULL                    COMMENT '文件大小(字节)',
    file_type   VARCHAR(50)     DEFAULT NULL                COMMENT '文件类型(MIME)',
    suffix      VARCHAR(20)     DEFAULT NULL                COMMENT '文件后缀',
    upload_by   BIGINT(20)      DEFAULT NULL                COMMENT '上传人ID',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '上传时间',
    PRIMARY KEY (id),
    KEY idx_upload_by (upload_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件上传记录表';

-- ============================================
-- 18. 操作日志表 (sys_log) 【新增】
-- 用于审计追踪
-- ============================================
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log (
    id          BIGINT(20)      NOT NULL AUTO_INCREMENT     COMMENT '日志ID',
    user_id     BIGINT(20)      DEFAULT NULL                COMMENT '用户ID',
    username    VARCHAR(50)     DEFAULT NULL                COMMENT '用户名',
    operation   VARCHAR(100)     DEFAULT NULL                COMMENT '操作描述',
    method      VARCHAR(200)     DEFAULT NULL                COMMENT '请求方法',
    params      TEXT            DEFAULT NULL                COMMENT '请求参数',
    result      TEXT            DEFAULT NULL                COMMENT '返回结果',
    ip          VARCHAR(50)      DEFAULT NULL                COMMENT 'IP地址',
    user_agent  VARCHAR(500)    DEFAULT NULL                COMMENT 'User-Agent',
    duration    INT             DEFAULT NULL                COMMENT '耗时(毫秒)',
    create_time DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================
-- 初始化测试数据
-- ============================================

-- 插入测试用户 (密码: 123456，明文存储)
INSERT INTO sys_user (id, username, password, nickname, avatar, phone, email, school, student_id, credit_score, level, balance, is_verified, status, deleted) VALUES
(1, 'admin',          '123456', '系统管理员', 'https://api.dicebear.com/7.x/avataaars/svg.jpgseed=Admin',   '13800138000', 'admin@jiaoyihang.com',   'XX大学', '2021000001', 100, 10, 1000.00, 1, 1, 0),
(2, 'study_lover',    '123456', '学霸小明',   'https://api.dicebear.com/7.x/avataaars/svg.jpgseed=Felix',  '138****8001', 'xiaoming@campus.edu',  'XX大学', '2021001234', 95, 8, 1000.00, 1, 1, 0),
(3, 'book_worm',      '123456', '书虫学姐',   'https://api.dicebear.com/7.x/avataaars/svg.jpgseed=Aneka',  '139****8002', 'xuejie@campus.edu',    'XX大学', '2020005678', 98, 12, 1000.00, 1, 1, 0),
(4, 'tech_geek',      '123456', '数码达人',   'https://api.dicebear.com/7.x/avataaars/svg.jpgseed=Max',    '137****8003', 'geek@campus.edu',      'XX大学', '2022003456', 92, 6, 1000.00, 1, 1, 0),
(5, 'runner_service', '123456', '跑腿小王',   'https://api.dicebear.com/7.x/avataaars/svg.jpgseed=Jacky', '136****8004', 'runner@campus.edu',    'XX大学', '2023007890', 88, 4, 1000.00, 1, 1, 0);

-- 插入商品分类
INSERT INTO sys_category (id, name, icon, color, sort_order, is_service) VALUES
('books',      '书籍教材', 'book',           '#1890ff', 1, 0),
('electronics','电子产品', 'laptop',         '#722ed1', 2, 0),
('daily',      '日常用品', 'shopping-cart',  '#52c41a', 3, 0),
('fashion',    '衣物鞋包', 'skin',           '#fa8c16', 4, 0),
('virtual',    '虚拟物品', 'cloud',          '#13c2c2', 5, 0),
('service',    '校园服务', 'team',           '#eb2f96', 6, 1),
('other',      '其他',     'appstore',        '#8c8c8c', 7, 0);

-- 插入测试商品
INSERT INTO sys_product (id, title, description, price, original_price, images, category_id, `condition`, status, view_count, favorite_count, seller_id, location, is_negotiable, tags, deleted, create_time, sold_time) VALUES
(1, '高等数学同济第七版 上下册全套', '高数教材，全新未使用，因为转专业不需要了。书本保存完好，无笔记痕迹。', 45.00, 78.00, '["https://picsum.photos/400/400?random=1/200/200.jpg","https://picsum.photos/400/400?random=2/200/200.jpg"]', 'books', '全新', '在售', 328, 56, 2, '校内 - 学生宿舍区', 1, '["高数","教材","考研"]', 0, NOW(), NULL),
(2, 'iPad Air 5 256G 紫色 WiFi版', '2022年购买，用了不到一年，现在换电脑了出掉。带原装充电器和保护套，无划痕。', 3200.00, 5499.00, '["https://picsum.photos/400/400?random=3/200/200.jpg","https://picsum.photos/400/400?random=4/200/200.jpg"]', 'electronics', '几乎全新', '在售', 1024, 128, 4, '校内 - 教学楼区', 1, '["iPad","平板","苹果"]', 0, NOW(), NULL),
(3, '小米台灯 简约风格 可调亮度', '去年买的，宿舍搬校区不用了。灯完好可用，包装配件齐全。', 65.00, 169.00, '["https://picsum.photos/400/400?random=5/200/200.jpg"]', 'daily', '轻微使用', '在售', 156, 23, 2, '校内 - 学生宿舍区', 0, '["台灯","小米","宿舍"]', 0, NOW(), NULL),
(4, '考研英语真题套装 张剑黄皮书', '英语一历年真题+解析，包含2010-2024年真题。无笔记，可放心使用。', 35.00, 89.00, '["https://picsum.photos/400/400?random=6/200/200.jpg"]', 'books', '轻微使用', '在售', 245, 42, 3, '校内 - 图书馆区', 1, '["考研","英语","真题"]', 0, NOW(), NULL),
(5, 'AirPods Pro 2代 全新未拆封', '朋友送的礼物，自己已有耳机，低价出。全新未拆封，可提供购买凭证。', 1400.00, 1899.00, '["https://picsum.photos/400/400?random=7/200/200.jpg"]', 'electronics', '全新', '在售', 856, 98, 4, '校内 - 食堂附近', 1, '["AirPods","耳机","苹果"]', 0, NOW(), NULL),
(6, '大学物理教材 力学热学光学', '物理专业教材，包括力学、热学、光学三本。有少量笔记，不影响使用。', 55.00, 120.00, '["https://picsum.photos/400/400?random=8/200/200.jpg"]', 'books', '正常使用', '在售', 89, 12, 2, '校内 - 学生宿舍区', 1, '["物理","教材","专业课"]', 0, NOW(), NULL),
(7, '折叠自行车 轻便型 适合校园', '毕业出自行车，折叠款方便携带。刹车灵敏，轮胎新换不久，骑行正常。', 280.00, 600.00, '["https://picsum.photos/400/400?random=9/200/200.jpg"]', 'daily', '轻微使用', '在售', 423, 67, 3, '校内 - 体育馆区', 1, '["自行车","出行","毕业出"]', 0, NOW(), NULL),
(8, '索尼 WH-1000XM4 头戴式耳机', '用了两年，降噪效果依然出色。耳罩有轻微使用痕迹，配件齐全。', 850.00, 2299.00, '["https://picsum.photos/400/400?random=10/200/200.jpg"]', 'electronics', '轻微使用', '在售', 567, 89, 4, '校内 - 教学楼区', 1, '["索尼","降噪耳机","头戴式"]', 0, NOW(), NULL),
(9, '机械键盘 Cherry青轴', '去年买的，用了三个月，键帽全新，换了静电容所以出掉。', 220.00, 450.00, '["https://picsum.photos/400/400?random=11/200/200.jpg"]', 'electronics', '轻微使用', '在售', 312, 44, 2, '校内 - 图书馆区', 0, '["键盘","机械键盘","Cherry"]', 0, NOW(), NULL),
(10, 'Switch OLED 日版 配原装底座', '游戏爱好者退坑出，机器无ban机记录，配件齐全附赠一张动森卡带。', 1650.00, 2599.00, '["https://picsum.photos/400/400?random=12","https://picsum.photos/400/400?random=13"]', 'electronics', '轻微使用', '在售', 789, 112, 3, '校内 - 食堂附近', 1, '["Switch","游戏机","任天堂"]', 0, NOW(), NULL),
(11, '线性代数教材 同济版', '大二线性代数用书，书页干净，无折痕，可作为考研参考。', 28.00, 55.00, '["https://picsum.photos/400/400?random=14/200/200.jpg"]', 'books', '轻微使用', '在售', 178, 31, 4, '校内 - 教学楼区', 1, '["线性代数","教材","考研"]', 0, NOW(), NULL),
(12, '宿舍小风扇 USB供电 静音款', '夏天必备，USB供电，宿舍可用三档调速，静音效果好。', 38.00, 79.00, '["https://picsum.photos/400/400?random=15/200/200.jpg"]', 'daily', '全新', '在售', 95, 15, 2, '校内 - 学生宿舍区', 0, '["风扇","宿舍","静音"]', 0, NOW(), NULL),

-- 插入测试服务
INSERT INTO sys_product (id, title, description, price, original_price, images, category_id, `condition`, status, view_count, favorite_count, seller_id, location, is_negotiable, tags, deleted, create_time, sold_time, type, service_type, price_unit, rating, rating_count, order_count) VALUES
(101, '取快递服务 - 校内全区域', '帮你取校内快递，菜鸟驿站、京东、顺丰均可，支持当日达。', 3.00, NULL, '[]', 'service', NULL, '可用', 234, 45, 2, '校内 - 菜鸟驿站', 0, '["取快递","校内","全区域"]', 0, NOW(), NULL, 'service', '取快递', '/次', 4.8, 56, 128),
(102, '带外卖 - 食堂到宿舍', '帮您从各大食堂带外卖到宿舍楼下，省去排队时间。', 2.00, NULL, '[]', 'service', NULL, '可用', 189, 38, 3, '校内 - 第一食堂', 0, '["带外卖","食堂","便捷"]', 0, NOW(), NULL, 'service', '带外卖', '/次', 4.9, 42, 95),
(103, '代买奶茶 - 一点点/COCO', '帮您去校外奶茶店买奶茶，支持一点点、COCO、喜茶等。', 5.00, NULL, '[]', 'service', NULL, '可用', 156, 29, 4, '校内 - 北门', 0, '["奶茶","代买","饮品"]', 0, NOW(), NULL, 'service', '代买', '/次', 4.7, 38, 67),
(104, '打印服务 - 论文/资料/照片', '提供打印服务，支持黑白/彩色打印，A4/A3纸张，量大优惠。', 0.20, NULL, '[]', 'service', NULL, '可用', 456, 78, 2, '校内 - 打印店', 0, '["打印","论文","资料"]', 0, NOW(), NULL, 'service', '打印', '/张', 4.9, 89, 234),
(105, '校园跑腿 - 紧急文件/物品', '紧急校内配送，30分钟内必达，支持文件、药品等急需品。', 10.00, NULL, '[]', 'service', NULL, '可用', 123, 22, 5, '校内 - 全区域', 0, '["跑腿","紧急","校内"]', 0, NOW(), NULL, 'service', '跑腿', '/次', 4.6, 25, 45),
(106, '代取代寄信件/包裹', '帮您代取或代寄信件、包裹，送到校门口或快递点。', 5.00, NULL, '[]', 'service', NULL, '可用', 98, 15, 3, '校内 - 收发室', 0, '["信件","包裹","代寄"]', 0, NOW(), NULL, 'service', '其他', '/次', 4.8, 18, 32);

-- 插入测试订单
INSERT INTO trade_order (order_no, type, product_id, buyer_id, seller_id, price, quantity, total_amount, status, contact, remark) VALUES
('ORD202604150001', '商品', 1, 3, 2, 45.00, 1, 45.00, '已完成', '139****8002', '教材很有用，感谢！'),
('ORD202604160001', '服务', NULL, 2, 5, 3.00, 1, 3.00, '待发货', '138****8001', '南门取快递，京东的'),
('ORD202604170001', '商品', 3, 4, 2, 65.00, 1, 65.00, '待发货', '137****8003', ''),
('ORD202604180001', '商品', 5, 3, 4, 1400.00, 1, 1400.00, '待付款', '139****8002', ''),
-- admin用户的测试订单（我卖出的）- 使用实际存在的商品ID
('ORD202604200001', '商品', 1, 2, 1, 88.00, 1, 88.00, '待发货', '138****8001', '尽快发货'),
('ORD202604200002', '商品', 2, 3, 1, 120.00, 1, 120.00, '待收货', '137****8003', ''),
('ORD202604200003', '商品', 3, 4, 1, 66.00, 1, 66.00, '已完成', '137****8003', '已收到，满意！'),
-- admin用户的测试订单（我买到的）- 使用实际存在的商品ID
('ORD202604200004', '商品', 4, 1, 2, 55.00, 1, 55.00, '待发货', '138****8001', '书收到了联系我'),
('ORD202604200005', '商品', 5, 1, 3, 299.00, 1, 299.00, '待付款', '138****8001', '');

-- 插入测试通知
INSERT INTO sys_notification (user_id, type, title, content, is_read, link) VALUES
(3, 'order',   '订单完成',     '您的订单 ORD202604150001 已完成', 0, '/orders/1'),
(3, 'system',  '信用提升',     '恭喜！您的信用分提升至 98 分', 0, NULL),
(2, 'activity', '新活动',      '校园交易节来啦！4月20日-25日，全场商品88折', 1, '/activity');

-- 插入测试收货地址
INSERT INTO ums_address (user_id, consignee, phone, province, city, district, street, is_default) VALUES
(2, '学霸小明', '138****8001', '广东省', '深圳市', '南山区', 'XX大学学生宿舍1号楼201', 1),
(3, '书虫学姐', '139****8002', '广东省', '广州市', '天河区', 'XX大学学生宿舍3号楼501', 1),
(4, '数码达人', '137****8003', '广东省', '深圳市', '福田区', 'XX大学学生宿舍5号楼302', 0);

-- 插入测试购物车数据
INSERT INTO trade_cart (user_id, product_id, quantity, is_selected) VALUES
(2, 2, 1, 1),
(2, 5, 1, 1),
(3, 7, 1, 0),
(4, 1, 2, 1);

-- 插入测试优惠券
INSERT INTO promo_coupon (name, type, condition_amount, reduce_amount, discount, total_stock, remain_stock, per_limit, valid_days, status) VALUES
('新用户专享券', 1, 50.00, 10.00, 1.00, 1000, 850, 1, 30, 1),
('满100减20',   1, 100.00, 20.00, 1.00, 500, 420, 2, 15, 1),
('8折折扣券',   2, 200.00, 0.00, 0.80, 200, 180, 1, 7, 1),
('无门槛5元券', 3, 0.00, 5.00, 1.00, 2000, 1950, 1, 3, 1);

-- 插入测试用户优惠券
INSERT INTO promo_user_coupon (user_id, coupon_id, status, expire_time) VALUES
(2, 1, 0, DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2, 2, 0, DATE_ADD(NOW(), INTERVAL 15 DAY)),
(3, 3, 0, DATE_ADD(NOW(), INTERVAL 7 DAY)),
(4, 1, 0, DATE_ADD(NOW(), INTERVAL 30 DAY)),
(4, 4, 0, DATE_ADD(NOW(), INTERVAL 3 DAY));

-- ============================================
-- 余额功能迁移脚本（给已有用户初始化余额）
-- 如已有用户，可单独执行以下 SQL
-- ============================================
-- ALTER TABLE sys_user ADD COLUMN balance DECIMAL(12,2) DEFAULT 1000.00 COMMENT '账户余额(元)';
-- UPDATE sys_user SET balance = 1000.00 WHERE balance IS NULL OR balance = 0;
