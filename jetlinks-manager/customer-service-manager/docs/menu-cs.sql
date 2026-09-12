-- ============================================================
-- 客服中心菜单与权限初始化（平台专属，scope=platform）
--
-- 与 tenant-manager/docs/menu-tenant.sql 同一套规则：
--   * 不走 PATCH /menu/{owner}/_all 批量导入（会先删光该 owner 的全部菜单）。
--   * 菜单 code 必须等于前端组件路径：customer-service-manager-ui 用
--     import.meta.glob('./views/**/index.vue') 建路由，cs/Inbox -> views/Inbox/index.vue。
--   * options 必须非空（前端查询 options nlike '%show":false%'，NULL 会被滤掉）。
--   * path 是物化路径：每级 4 字符、'-' 连接，_level 等于段数。
--   * 角色的实际权限由「角色绑定的菜单」在登录时推导
--     （MenuAuthenticationInitializeService），并与 s_permission 里的定义取交集，
--     所以 cs-inbox / cs-lead / cs-session 必须先存在于 s_permission，否则推导结果为空。
--   * 直接写库不触发菜单缓存失效，执行后需重启 jetlinks-api；已登录用户需重新登录。
-- 幂等，可重复执行。
-- ============================================================

-- 1. 权限定义
INSERT INTO s_permission (id, name, describe, status, actions, create_time)
VALUES
    ('cs-inbox', '客服留言', '官网留言箱', 1,
     '[{"action":"query","name":"查询"},{"action":"save","name":"保存"}]',
     (extract(epoch FROM now()) * 1000)::bigint),
    ('cs-lead', '客服线索', '售前线索与跟进', 1,
     '[{"action":"query","name":"查询"},{"action":"save","name":"保存"},{"action":"delete","name":"删除"}]',
     (extract(epoch FROM now()) * 1000)::bigint),
    ('cs-session', '客服在线会话', '坐席工作台与在线会话', 1,
     '[{"action":"query","name":"查询"},{"action":"save","name":"保存"}]',
     (extract(epoch FROM now()) * 1000)::bigint),
    ('cs-faq', '客服常见问题', '官网常见问题与坐席常用回复', 1,
     '[{"action":"query","name":"查询"},{"action":"save","name":"保存"},{"action":"delete","name":"删除"}]',
     (extract(epoch FROM now()) * 1000)::bigint)
ON CONFLICT (id) DO UPDATE SET
    name = excluded.name, describe = excluded.describe, status = 1, actions = excluded.actions;

-- 2. 菜单：一级「客服中心」 + 坐席工作台 + 留言箱 + 线索管理 + 常见问题
INSERT INTO s_menu (
    id, parent_id, path, sort_index, _level, owner, name, code, url, icon,
    status, scope, permissions, buttons, options, i18n_messages, create_time
) VALUES
(
    'cs0center000group0menu0000000001', NULL, 'Cs0a', 6, 1, 'iot',
    '客服中心', 'cs', '/cs', 'icon-tongzhiguanli', 1, 'platform',
    '[]', '[]',
    '{"show":true,"appName":"customer-service-manager"}',
    '{"name":{"zh_CN":"客服中心","en_US":"Customer Service"}}',
    (extract(epoch FROM now()) * 1000)::bigint
),
(
    'cs0inbox0000menu0000000000000002', 'cs0center000group0menu0000000001', 'Cs0a-Cs0b', 1, 2, 'iot',
    '留言箱', 'cs/Inbox', '/cs/Inbox', 'icon-tongzhiguanli', 1, 'platform',
    '[{"permission":"cs-inbox","actions":["query","save"]}]',
    '[{"id":"view","name":"查看","permissions":[{"permission":"cs-inbox","actions":["query"]}]},'
    || '{"id":"read","name":"标记已读","permissions":[{"permission":"cs-inbox","actions":["save"]}]}]',
    '{"show":true,"appName":"customer-service-manager"}',
    '{"name":{"zh_CN":"留言箱","en_US":"Inbox"}}',
    (extract(epoch FROM now()) * 1000)::bigint
),
(
    'cs0lead00000menu0000000000000003', 'cs0center000group0menu0000000001', 'Cs0a-Cs0c', 2, 2, 'iot',
    '线索管理', 'cs/Lead', '/cs/Lead', 'icon-zuzhi', 1, 'platform',
    '[{"permission":"cs-lead","actions":["query","save","delete"]},{"permission":"tenant","actions":["query","save"]}]',
    '[{"id":"view","name":"查看","permissions":[{"permission":"cs-lead","actions":["query"]}]},'
    || '{"id":"add","name":"录入线索","permissions":[{"permission":"cs-lead","actions":["save"]}]},'
    || '{"id":"update","name":"编辑","permissions":[{"permission":"cs-lead","actions":["save"]}]},'
    || '{"id":"claim","name":"认领","permissions":[{"permission":"cs-lead","actions":["save"]}]},'
    || '{"id":"assign","name":"指派","permissions":[{"permission":"cs-lead","actions":["save"]}]},'
    || '{"id":"follow","name":"跟进","permissions":[{"permission":"cs-lead","actions":["save"]}]},'
    || '{"id":"convert","name":"转化为租户","permissions":[{"permission":"cs-lead","actions":["save"]},{"permission":"tenant","actions":["query","save"]}]},'
    || '{"id":"export","name":"导出","permissions":[{"permission":"cs-lead","actions":["query"]}]},'
    || '{"id":"delete","name":"删除","permissions":[{"permission":"cs-lead","actions":["delete"]}]}]',
    '{"show":true,"appName":"customer-service-manager"}',
    '{"name":{"zh_CN":"线索管理","en_US":"Leads"}}',
    (extract(epoch FROM now()) * 1000)::bigint
),
(
    'cs0workbenchmenu0000000000000004', 'cs0center000group0menu0000000001', 'Cs0a-Cs0d', 0, 2, 'iot',
    '坐席工作台', 'cs/Workbench', '/cs/Workbench', 'icon-tongzhiguanli', 1, 'platform',
    '[{"permission":"cs-session","actions":["query","save"]},{"permission":"cs-lead","actions":["save"]}]',
    '[{"id":"view","name":"查看","permissions":[{"permission":"cs-session","actions":["query"]}]},'
    || '{"id":"chat","name":"接待与回复","permissions":[{"permission":"cs-session","actions":["save"]}]},'
    || '{"id":"transfer","name":"转接","permissions":[{"permission":"cs-session","actions":["save"]}]},'
    || '{"id":"lead","name":"转为线索","permissions":[{"permission":"cs-session","actions":["save"]},{"permission":"cs-lead","actions":["save"]}]}]',
    '{"show":true,"appName":"customer-service-manager"}',
    '{"name":{"zh_CN":"坐席工作台","en_US":"Workbench"}}',
    (extract(epoch FROM now()) * 1000)::bigint
),
(
    'cs0faq000000menu0000000000000005', 'cs0center000group0menu0000000001', 'Cs0a-Cs0e', 3, 2, 'iot',
    '常见问题', 'cs/Faq', '/cs/Faq', 'icon-tongzhiguanli', 1, 'platform',
    '[{"permission":"cs-faq","actions":["query","save","delete"]}]',
    '[{"id":"view","name":"查看","permissions":[{"permission":"cs-faq","actions":["query"]}]},'
    || '{"id":"add","name":"新增","permissions":[{"permission":"cs-faq","actions":["save"]}]},'
    || '{"id":"update","name":"编辑","permissions":[{"permission":"cs-faq","actions":["save"]}]},'
    || '{"id":"delete","name":"删除","permissions":[{"permission":"cs-faq","actions":["delete"]}]}]',
    '{"show":true,"appName":"customer-service-manager"}',
    '{"name":{"zh_CN":"常见问题","en_US":"FAQ"}}',
    (extract(epoch FROM now()) * 1000)::bigint
)
ON CONFLICT (id) DO UPDATE SET
    parent_id = excluded.parent_id, path = excluded.path, _level = excluded._level,
    name = excluded.name, code = excluded.code, url = excluded.url, icon = excluded.icon,
    scope = excluded.scope, status = 1, sort_index = excluded.sort_index,
    permissions = excluded.permissions, buttons = excluded.buttons,
    options = excluded.options, i18n_messages = excluded.i18n_messages;

-- 3. 绑定到「客服」「客服主管」角色（角色由 CsRoleInitializer 启动时创建）
--    target_key = md5('role|<roleId>')，id = md5('<roleId>|<menuId>')，与租户脚本一致。
INSERT INTO s_menu_bind (id, target_type, target_id, target_key, menu_id, owner, options, buttons)
SELECT md5(r.id || '|' || m.id), 'role', r.id, md5('role|' || r.id), m.id, 'iot',
       m.options, m.buttons
FROM s_role r
CROSS JOIN s_menu m
WHERE r.id IN ('cs-agent', 'cs-supervisor')
  AND m.id IN ('cs0center000group0menu0000000001', 'cs0inbox0000menu0000000000000002', 'cs0lead00000menu0000000000000003', 'cs0workbenchmenu0000000000000004', 'cs0faq000000menu0000000000000005')
ON CONFLICT (id) DO UPDATE SET options = excluded.options, buttons = excluded.buttons;

-- 校验
SELECT m.code, m.name, m._level, m.sort_index, m.scope, count(b.id) AS bound_roles
FROM s_menu m LEFT JOIN s_menu_bind b ON b.menu_id = m.id AND b.target_type = 'role'
WHERE m.code IN ('cs', 'cs/Workbench', 'cs/Inbox', 'cs/Lead', 'cs/Faq')
GROUP BY m.id ORDER BY m._level, m.sort_index;

SELECT id, name, status FROM s_permission WHERE id IN ('cs-inbox', 'cs-lead', 'cs-session', 'cs-faq');
SELECT id, name FROM s_role WHERE id IN ('cs-agent', 'cs-supervisor');
