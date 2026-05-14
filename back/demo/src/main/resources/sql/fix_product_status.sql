-- ============================================
-- 数据修复脚本：商品状态与订单状态同步
-- 执行时机：上线后修复历史数据，之后由代码自动维护
-- ============================================

USE jiaoyihang;

-- 1. 商品：已有"已完成"或"待发货/待收货"订单的 → 已售出
--    （只要存在未取消的订单，说明商品已成交）
UPDATE sys_product p
SET p.status = '已售出',
    p.update_time = NOW(),
    p.sold_time = NOW()
WHERE p.deleted = 0
  AND p.type = 'product'
  AND p.status != '已售出'
  AND EXISTS (
      SELECT 1 FROM trade_order o
      WHERE o.product_id = p.id
        AND o.deleted = 0
        AND o.status = '已完成'
  );

-- 2. 商品：已有"待发货/待收货"订单的 → 已预订
UPDATE sys_product p
SET p.status = '已预订',
    p.update_time = NOW()
WHERE p.deleted = 0
  AND p.type = 'product'
  AND p.status NOT IN ('已售出', '已预订')
  AND EXISTS (
      SELECT 1 FROM trade_order o
      WHERE o.product_id = p.id
        AND o.deleted = 0
        AND o.status IN ('待付款', '待发货', '待收货')
  );

-- 3. 服务：已有"已完成"订单的 → 已完成
UPDATE sys_product p
SET p.status = '已完成',
    p.update_time = NOW()
WHERE p.deleted = 0
  AND p.type = 'service'
  AND p.status != '已完成'
  AND EXISTS (
      SELECT 1 FROM trade_order o
      WHERE o.product_id = p.id
        AND o.deleted = 0
        AND o.status = '已完成'
  );

-- 4. 服务：已有"待发货/待收货"订单的 → 已预订
UPDATE sys_product p
SET p.status = '已预订',
    p.update_time = NOW()
WHERE p.deleted = 0
  AND p.type = 'service'
  AND p.status NOT IN ('已完成', '已预订')
  AND EXISTS (
      SELECT 1 FROM trade_order o
      WHERE o.product_id = p.id
        AND o.deleted = 0
        AND o.status IN ('待付款', '待发货', '待收货')
  );

-- 5. 清理幽灵商品：
--    有"已取消"或无订单 且 状态是"已预订/已售出/已完成"的 → 恢复为在售/可用
UPDATE sys_product p
SET p.status = CASE p.type WHEN 'service' THEN '可用' ELSE '在售' END,
    p.update_time = NOW()
WHERE p.deleted = 0
  AND (
      (p.type = 'product' AND p.status IN ('已预订', '已售出'))
      OR (p.type = 'service' AND p.status IN ('已预订', '已完成'))
  )
  AND NOT EXISTS (
      SELECT 1 FROM trade_order o
      WHERE o.product_id = p.id AND o.deleted = 0
        AND o.status NOT IN ('已取消', '已删除')
  );

SELECT '修复完成，商品状态已与订单同步' AS result;
