-- ============================================================
-- 支付中心菜单与权限初始化（平台专属，scope=platform）
--
-- 规则同 customer-service-manager/docs/menu-cs.sql：
--   * 菜单 code 等于前端组件路径：pay/order -> src/views/pay/order/index.vue（根视图，不属于子模块）。
--   * 收银台 /pay/checkout/:id 是静态路由（src/router/basic.ts），不需要菜单。
--   * options 必须非空（前端查询 options nlike '%show":false%'，NULL 会被滤掉）。
--   * path 是物化路径：每级 4 字符、'-' 连接，_level 等于段数。
--   * 角色实际权限 = 角色绑定的菜单 ∩ s_permission，所以 pay-order 必须先写进 s_permission。
--   * 平台管理员默认可见全部菜单，这里不绑定角色；财务等角色在「角色管理」里勾选。
--   * 直接写库不触发菜单缓存失效，执行后需重启 jetlinks-api；已登录用户需重新登录。
-- 幂等，可重复执行。
-- ============================================================

INSERT INTO s_permission (id, name, describe, status, actions, create_time)
VALUES
    ('pay-order', '支付订单', '支付单查询、确认线下到账与关闭', 1,
     '[{"action":"query","name":"查询"},{"action":"save","name":"保存"}]',
     (extract(epoch FROM now()) * 1000)::bigint)
ON CONFLICT (id) DO UPDATE SET
    name = excluded.name, describe = excluded.describe, status = 1, actions = excluded.actions;

INSERT INTO s_menu (
    id, parent_id, path, sort_index, _level, owner, name, code, url, icon,
    status, scope, permissions, buttons, options, i18n_messages, create_time
) VALUES
(
    'pay0center00group0menu0000000001', NULL, 'Pa0a', 7, 1, 'iot',
    '支付中心', 'pay', '/pay', 'icon-tongzhiguanli', 1, 'platform',
    '[]', '[]',
    '{"show":true}',
    '{"name":{"zh_CN":"支付中心","en_US":"Payment"}}',
    (extract(epoch FROM now()) * 1000)::bigint
),
(
    'pay0order000menu0000000000000002', 'pay0center00group0menu0000000001', 'Pa0a-Pa0b', 1, 2, 'iot',
    '支付订单', 'pay/order', '/pay/order', 'icon-tongzhiguanli', 1, 'platform',
    '[{"permission":"pay-order","actions":["query","save"]}]',
    '[{"id":"view","name":"查看","permissions":[{"permission":"pay-order","actions":["query"]}]},'
    || '{"id":"confirm","name":"确认到账","permissions":[{"permission":"pay-order","actions":["save"]}]},'
    || '{"id":"close","name":"关闭","permissions":[{"permission":"pay-order","actions":["save"]}]}]',
    '{"show":true}',
    '{"name":{"zh_CN":"支付订单","en_US":"Pay Orders"}}',
    (extract(epoch FROM now()) * 1000)::bigint
)
ON CONFLICT (id) DO UPDATE SET
    parent_id = excluded.parent_id, path = excluded.path, _level = excluded._level,
    name = excluded.name, code = excluded.code, url = excluded.url, icon = excluded.icon,
    scope = excluded.scope, status = 1, sort_index = excluded.sort_index,
    permissions = excluded.permissions, buttons = excluded.buttons,
    options = excluded.options, i18n_messages = excluded.i18n_messages;

-- 校验
SELECT code, name, _level, sort_index, scope FROM s_menu WHERE code IN ('pay', 'pay/order') ORDER BY _level;
SELECT id, name, status FROM s_permission WHERE id = 'pay-order';
