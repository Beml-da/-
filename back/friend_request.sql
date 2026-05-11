-- 好友申请表（加好友请求）
CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    from_user_id BIGINT NOT NULL COMMENT '申请人用户ID',
    to_user_id BIGINT NOT NULL COMMENT '被申请人用户ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0=待处理，1=已同意，2=已拒绝',
    message VARCHAR(255) DEFAULT NULL COMMENT '申请留言',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0=未删除，1=已删除',
    INDEX idx_from_user (from_user_id),
    INDEX idx_to_user (to_user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';

-- 好友关系表
CREATE TABLE IF NOT EXISTS friend_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    friend_id BIGINT NOT NULL COMMENT '好友用户ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0=未删除，1=已删除',
    UNIQUE KEY uk_friend (user_id, friend_id),
    INDEX idx_user_id (user_id),
    INDEX idx_friend_id (friend_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';
