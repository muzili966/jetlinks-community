-- ============================================================
-- 租户相关菜单初始化（平台专属，scope=platform）
--
-- 为什么直接写库而非走 UI 导入：
--   批量导入接口 PATCH /menu/{owner}/_all 会先删光该 owner 下全部菜单再插入，
--   直接用会清掉现有 38 个业务菜单。
--
-- path 规则：父path-4位串，长度需与同级菜单一致（hsweb 树形规范）。
-- 幂等，可重复执行。
-- ============================================================

DO $$
DECLARE
    v_parent   text := 'fd1670b860ae58cc58bcd01d027ccd35';  -- system 系统管理
    parent_path text;
    parent_lvl  int;
    m           record;
BEGIN
    SELECT path, _level INTO parent_path, parent_lvl FROM s_menu WHERE id = v_parent;

    FOR m IN
        SELECT * FROM (VALUES
            ('tenant0manage0menu00000000000001', 'system/Tenant',        '租户管理', 'icon-zuzhi',     'Tn7X', 90, 'tenant'),
            ('tenant0plan000menu00000000000002', 'system/TenantPlan',    '订阅套餐', 'icon-Component', 'Tn7Y', 91, 'tenant'),
            ('tenant0order00menu00000000000003', 'system/TenantOrder',   '订单流水', 'icon-wenjian',   'Tn7Z', 92, 'tenant'),
            ('tenant0invoic0menu00000000000004', 'system/TenantInvoice', '发票管理', 'icon-wenjian',   'Tn80', 93, 'tenant')
        ) AS t(mid, code, mname, icon, seg, sort, perm)
    LOOP
        INSERT INTO s_menu (
            id, parent_id, path, sort_index, _level, owner, name, code, url, icon,
            status, scope, permissions, buttons, i18n_messages, create_time
        ) VALUES (
            m.mid, v_parent, parent_path || '-' || m.seg, m.sort, parent_lvl + 1, 'iot',
            m.mname, m.code, '/' || m.code, m.icon, 1,
            'platform',   -- 平台专属：不得授予租户角色
            format('[{"permission":"%s","actions":["query","save","delete"]}]', m.perm)::text,
            format('[{"id":"view","name":"查看","permissions":[{"permission":"%s","actions":["query"]}]},'
                || '{"id":"update","name":"编辑","permissions":[{"permission":"%s","actions":["save"]}]},'
                || '{"id":"delete","name":"删除","permissions":[{"permission":"%s","actions":["delete"]}]}]',
                m.perm, m.perm, m.perm)::text,
            format('{"name":{"zh_CN":"%s"}}', m.mname)::text,
            (extract(epoch FROM now()) * 1000)::bigint
        )
        ON CONFLICT (id) DO UPDATE SET
            name = excluded.name, code = excluded.code, url = excluded.url,
            scope = excluded.scope, status = 1, sort_index = excluded.sort_index;
    END LOOP;
END $$;

-- 平台专属菜单不应授予任何租户角色：清理误授权
DELETE FROM s_menu_bind
WHERE menu_id IN (SELECT id FROM s_menu WHERE scope = 'platform')
  AND target_type = 'role'
  AND target_id <> 'platform-admin';

SELECT code, name, scope, path FROM s_menu WHERE scope = 'platform' ORDER BY sort_index;

-- ============================================================
-- 租户自助订阅菜单（scope=tenant，授予租户角色）
--
-- 为什么必须写 options：
--   真机踩坑——只建菜单不填 options 时，菜单能在授权树里看到，
--   PUT /menu/role/{id}/_grant 也返回 success，但 granted 始终为 false，
--   租户端菜单树里也不出现。补上 options({show,appName}) 后立刻正常。
--   permissions/buttons 同理留空数组而非 NULL。
--
-- 菜单 code 必须等于组件路径：authentication-manager-ui 模块用
--   import.meta.glob('./views/**/index.vue') 建路由，
--   code=account/Subscription -> views/account/Subscription/index.vue。
--   接口 /my/subscription/** 是 @Authorize(merge=false)，只需登录，
--   所以 permissions 为空数组即可。
-- 幂等，可重复执行。
-- ============================================================

DO $$
DECLARE
    v_menu   text := 'tenant0my0subscription0menu00001';
BEGIN
    -- 顶级菜单: 「个人中心」自身是叶子页面(url=/account/center),
    -- 挂它下面的子菜单侧边栏不渲染(真机踩坑), 故直接置于顶级。
    INSERT INTO s_menu (
        id, parent_id, path, sort_index, _level, owner, name, code, url, icon,
        status, scope, permissions, buttons, options, i18n_messages, create_time
    ) VALUES (
        v_menu, NULL, 'TnSb', 5, 1, 'iot',
        '我的订阅', 'account/Subscription', '/account/Subscription', 'icon-Component', 1,
        'tenant',
        '[]',
        '[{"id":"view","name":"查看","permissions":[],"i18nMessages":{"zh_CN":"查看","en_US":"View"}}]',
        '{"show":true,"appName":"authentication-manager"}',
        '{"name":{"zh_CN":"我的订阅","en_US":"My Subscription"}}',
        (extract(epoch FROM now()) * 1000)::bigint
    )
    ON CONFLICT (id) DO UPDATE SET
        name = excluded.name, code = excluded.code, url = excluded.url,
        scope = excluded.scope, status = 1,
        permissions = excluded.permissions,
        buttons = excluded.buttons,
        options = excluded.options;

    -- 绑定到租户角色；target_key = md5('role|<roleId>')
    INSERT INTO s_menu_bind (id, target_type, target_id, target_key, menu_id, owner, options, buttons)
    SELECT md5(r.id || '|' || v_menu), 'role', r.id, md5('role|' || r.id), v_menu, 'iot',
           '{"show":true,"appName":"authentication-manager"}',
           '[{"id":"view","name":"查看","permissions":[],"i18nMessages":{"zh_CN":"查看","en_US":"View"}}]'
    FROM s_role r
    WHERE r.id = 'tenant-user' OR r.id LIKE 'tenant-admin-%'
    ON CONFLICT (id) DO NOTHING;
END $$;

SELECT m.code, m.name, m.scope, b.target_id AS granted_role
FROM s_menu m LEFT JOIN s_menu_bind b ON b.menu_id = m.id
WHERE m.code = 'account/Subscription';

-- ============================================================
-- 平台专属菜单收口
--
-- 判定标准是「租户操作它会不会影响别的租户或平台本身」，不是功能重要性：
--   1) 全局唯一或全租户共享的配置——租户改了会串到其他租户
--   2) 触及宿主机资源(端口)或能在平台内执行代码(协议 jar / 插件)
--
-- 注意这里收的是「管理入口」：租户仍可读取这些数据（建产品时选分类、
-- 选协议），只是不能增删改。故不给这些实体加 tenant_id 列。
--
-- 与 PlatformMenuGuard.PLATFORM_ONLY_MENUS 保持一致——两处都要维护是刻意的：
-- scope 是运行时依据（改菜单即可生效），代码清单是 scope 未设置时的兜底。
-- 幂等，可重复执行。
-- ============================================================

UPDATE s_menu SET scope = 'platform'
WHERE code IN (
    -- 租户与计费
    'tenant-ops',          -- 租户运营分组
    'system/Tenant', 'system/TenantPlan', 'system/TenantOrder', 'system/TenantInvoice',
    -- 授权体系：授予租户即构成提权
    'system/Menu', 'system/Permission', 'system/Platforms',
    -- 全局配置
    'system/Basis',        -- 系统名称/LOGO/前端地址，单实例唯一
    'system/Dictionary',   -- 全局枚举字典
    'system/Relationship', -- 全局关系元数据
    'device/Category',     -- 全局产品分类树
    -- 平台基础设施
    'link/Protocol',       -- 协议包上传 = 平台内执行任意代码
    'link/Type',           -- 网络组件占用平台端口
    'link/plugin',         -- 平台级扩展
    'link/DashBoard'       -- 运维仪表盘：整页为宿主机 CPU/内存/JVM 指标
);

-- 撤销租户角色对平台专属菜单的既有授权
DELETE FROM s_menu_bind
WHERE target_type = 'role'
  AND target_id <> 'platform-admin'
  AND menu_id IN (SELECT id FROM s_menu WHERE scope = 'platform');

-- 「订阅管理」实为用户的通知订阅，与租户订阅套餐同名易混淆，改名区分
UPDATE s_menu
SET name = '通知订阅',
    i18n_messages = '{"name":{"zh_CN":"通知订阅","en_US":"Notification Subscription"}}'
WHERE code = 'system/NoticeRule';

SELECT code, name, scope FROM s_menu WHERE scope = 'platform' ORDER BY code;

-- ============================================================
-- 关于 scope 字段：本脚本里的 scope 赋值只是「初始化」，不是「保障」
--
-- scope 极易被无意抹掉，真机实测过两条路径：
--   1) 菜单编辑表单不含 scope 字段，保存任意菜单时实体层的
--      @DefaultValue("tenant") 会把它填成 tenant，覆盖掉 platform；
--   2) 「菜单管理 → 同步菜单」走 PATCH /menu/{owner}/_all，
--      先删光该 owner 的菜单再重建，连 upsert 的 coalesce 都轮不上。
--   实测：提交时剥掉 scope，15 个 platform 菜单全部退化成 tenant。
--
-- 因此 platform / tenant-only 两类的 scope 由代码兜底回填，见
--   tenant-manager/interceptor/TenantMenuScopeListener
--   tenant-manager/role/PlatformMenuGuard（两份 code 清单）
-- 改这两类菜单的归属要改代码清单，光改库会在下次保存时被回填覆盖。
-- ============================================================

-- ============================================================
-- 租户运营菜单收敛
--
-- 把散落在「系统管理」下的 4 个平台运营菜单收进独立的一级分组「租户运营」，
-- 与「系统管理」（用户/角色/字典等平台配置）并列——它们是 SaaS 的业务域，
-- 不是系统配置的一部分。
--
-- 三条真机踩过的注意事项：
--   1) 上面几段的 ON CONFLICT DO UPDATE 不含 parent_id/path/_level，
--      靠重跑那些语句迁不动层级，必须像下面这样显式 UPDATE。
--   2) path 是物化路径：每级 4 字符、'-' 连接，_level 等于段数。
--      手写容易和同级长度不一致，正常应走 PATCH /menu 由
--      TreeSortServiceHelper.refactorPath() 自动重算；这里给出等价的 SQL
--      仅供全新环境初始化。
--   3) 直接写库不触发 DefaultMenuService 的实体事件，menus-cache 与
--      认证缓存都不会自动失效——跑完必须重启，或对每条菜单再 PATCH 一次。
--
-- code 一律保持不变：前端用 code 去 import.meta.glob 的结果里找页面组件
-- （system/Tenant -> views/system/Tenant/index.vue），改 code 会静默渲染空白页。
-- 幂等，可重复执行。
-- ============================================================

INSERT INTO s_menu (
    id, parent_id, path, sort_index, _level, owner, name, code, url, icon,
    status, scope, options, i18n_messages, create_time
) VALUES (
    'tenant0ops000group0menu000000001', NULL, 'Mto5', 5, 1, 'iot',
    '租户运营', 'tenant-ops', '/tenant-ops', 'icon-zuzhi', 1, 'platform',
    -- options 必须非空：前端菜单查询带 `options nlike '%show":false%'`，
    -- NULL NOT LIKE 求值为 NULL，整行会被静默滤掉
    '{"show":true,"appName":"authentication-manager"}',
    '{"name":{"zh_CN":"租户运营","en_US":"Tenant Operations"}}',
    (extract(epoch FROM now()) * 1000)::bigint
)
ON CONFLICT (id) DO UPDATE SET
    name = excluded.name, url = excluded.url, scope = excluded.scope,
    options = excluded.options, status = 1, sort_index = excluded.sort_index;

-- 迁移 4 个子菜单（显式改 parent_id/path/_level，ON CONFLICT 那套覆盖不到）
UPDATE s_menu SET
    parent_id = 'tenant0ops000group0menu000000001',
    path      = 'Mto5-' || substr(md5(code), 1, 4),
    _level    = 2,
    sort_index = CASE code
        WHEN 'system/Tenant'        THEN 1
        WHEN 'system/TenantPlan'    THEN 2
        WHEN 'system/TenantOrder'   THEN 3
        WHEN 'system/TenantInvoice' THEN 4 END,
    options = coalesce(options, '{"show":true,"appName":"authentication-manager"}')
WHERE code IN ('system/Tenant', 'system/TenantPlan', 'system/TenantOrder', 'system/TenantInvoice');

-- 「订单流水」改名：它记的是订阅套餐的购买/续费流水，叫「订阅订单」更贴切
UPDATE s_menu
SET name = '订阅订单',
    i18n_messages = '{"name":{"zh_CN":"订阅订单","en_US":"Subscription Orders"}}'
WHERE code = 'system/TenantOrder';

-- 「我的订阅」是租户专属：平台管理员走 isAllowAllMenu 全量分支能看到它，
-- 但点进去后端返回 404（平台账号不属于任何租户）。用 options.tenantOnly 打标，
-- 由前端 store/menu.ts 的 filterTenantOnly 对非租户账号剔除。
-- 不下发 scope 作为查询条件：租户端查的是 s_menu_bind，那张表没有 scope 列，
-- 实测会让整棵菜单树返回 0 条。
UPDATE s_menu
SET scope = 'tenant-only',
    sort_index = 6,
    options = '{"show":true,"appName":"authentication-manager","tenantOnly":true}'
WHERE code = 'account/Subscription';

SELECT m.code, m.name, m._level, m.sort_index, m.scope, p.code AS parent
FROM s_menu m LEFT JOIN s_menu p ON p.id = m.parent_id
WHERE m.code = 'tenant-ops' OR p.code = 'tenant-ops' OR m.code = 'account/Subscription'
ORDER BY m._level, m.sort_index;
