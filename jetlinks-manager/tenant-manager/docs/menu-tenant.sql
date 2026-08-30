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
    parent_id  text := 'fd1670b860ae58cc58bcd01d027ccd35';  -- system 系统管理
    parent_path text;
    parent_lvl  int;
    m           record;
BEGIN
    SELECT path, _level INTO parent_path, parent_lvl FROM s_menu WHERE id = parent_id;

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
            m.mid, parent_id, parent_path || '-' || m.seg, m.sort, parent_lvl + 1, 'iot',
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
