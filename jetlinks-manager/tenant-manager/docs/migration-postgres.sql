-- ============================================================
-- JetLinks 多租户改造 · 存量数据迁移脚本 (PostgreSQL)
-- 幂等可重复执行. 执行前提: 已启用 tenant.enabled=true 并启动过一次
-- (AutoDDL 会自动补 tenant_id 列, 本脚本兜底补列 + 归属存量数据 + 建索引)
-- ============================================================

-- 1. 默认租户
INSERT INTO s_tenant (id, name, describe, state, create_time)
VALUES ('default', '默认租户', '存量数据迁移自动创建', 'enabled',
        (extract(epoch from now()) * 1000)::bigint)
ON CONFLICT (id) DO NOTHING;

-- 2. 平台管理员角色(D5: 拥有此角色的用户跨租户, 全程审计)
INSERT INTO s_role (id, name, description)
VALUES ('platform-admin', '平台管理员', '跨租户平台管理角色, 不受租户数据过滤')
ON CONFLICT (id) DO NOTHING;

-- 3. admin 用户绑定平台管理员角色
INSERT INTO s_dimension_user (id, dimension_type_id, dimension_id, dimension_name, user_id, user_name)
SELECT 'admin-platform-admin', 'role', 'platform-admin', '平台管理员', u.id, u.name
FROM s_user u
WHERE u.username = 'admin'
ON CONFLICT (id) DO NOTHING;

-- 4. 其余现有用户全部归入默认租户
INSERT INTO s_dimension_user (id, dimension_type_id, dimension_id, dimension_name, user_id, user_name)
SELECT 'tenant-default-' || u.id, 'tenant', 'default', '默认租户', u.id, u.name
FROM s_user u
WHERE u.username <> 'admin'
ON CONFLICT (id) DO NOTHING;

-- 5. 各业务表: 兜底补列 + 存量归属默认租户 + 租户索引
DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        'dev_device_instance', 'dev_product', 'dev_device_tags',
        'dev_metadata_mapping', 'dev_transparent_codec',
        'network_config', 'device_gateway', 'certificate_info',
        'rule_instance', 'rule_scene', 's_alarm_rule_bind',
        'alarm_config', 'alarm_record', 'alarm_handle_history',
        'notify_config', 'notify_template',
        's_organization', 's_role', 's_role_group', 's_user_detail',
        's_third_party_user_bind', 's_menu_bind',
        's_file', 'thing_property_metric', 's_object_related'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE IF EXISTS %I ADD COLUMN IF NOT EXISTS tenant_id varchar(64)', t);
        EXECUTE format('UPDATE %I SET tenant_id = ''default'' WHERE tenant_id IS NULL', t);
        -- tenant_id 单列索引; 高频表建议按查询模式追加复合索引(tenant_id 最左)
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_tenant ON %I (tenant_id)', t, t);
    END LOOP;
END $$;

-- 6. 高频表复合索引(tenant_id 最左)
CREATE INDEX IF NOT EXISTS idx_dev_instance_tenant_product ON dev_device_instance (tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_dev_instance_tenant_state   ON dev_device_instance (tenant_id, state);
CREATE INDEX IF NOT EXISTS idx_alarm_record_tenant_state   ON alarm_record (tenant_id, state);

-- 7. 平台管理员角色不归属任何租户(NULL), 修正上一步误刷
UPDATE s_role SET tenant_id = NULL WHERE id = 'platform-admin';

-- ============================================================
-- V2 补充: 订阅套餐(表由AutoDDL创建, 此处为种子数据兜底与新列)
-- ============================================================

-- 8. s_tenant 订阅列兜底(AutoDDL 会补, 幂等)
ALTER TABLE IF EXISTS s_tenant ADD COLUMN IF NOT EXISTS plan_id varchar(64);
ALTER TABLE IF EXISTS s_tenant ADD COLUMN IF NOT EXISTS subscribe_expire_time bigint;

-- 9. 内置三档套餐种子(应用启动时表为空也会自动初始化, 此处幂等兜底)
INSERT INTO s_tenant_plan (id, name, monthly_price, quota, sort_index, state, describe, create_time)
VALUES
  ('free', '免费版', 0,
   '{"maxDeviceCount":10,"maxProductCount":2,"dataRetentionDays":7}', 0, 'enabled',
   '体验用途: 10设备/2产品/数据保留7天', (extract(epoch from now()) * 1000)::bigint),
  ('standard', '标准版', 1800,
   '{"maxDeviceCount":1000,"maxProductCount":50,"dataRetentionDays":90}', 1, 'enabled',
   '中小规模: 1000设备/50产品/数据保留90天', (extract(epoch from now()) * 1000)::bigint),
  ('ultimate', '旗舰版', 3600,
   '{"maxDeviceCount":10000,"maxProductCount":500,"dataRetentionDays":365}', 2, 'enabled',
   '生产规模: 10000设备/500产品/数据保留365天', (extract(epoch from now()) * 1000)::bigint)
ON CONFLICT (id) DO NOTHING;

-- 10. 存量租户默认订阅免费版
UPDATE s_tenant SET plan_id = 'free' WHERE plan_id IS NULL;
