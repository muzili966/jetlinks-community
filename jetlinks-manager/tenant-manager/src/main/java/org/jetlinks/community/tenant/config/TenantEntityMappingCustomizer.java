package org.jetlinks.community.tenant.config;

import org.hswebframework.web.crud.entity.factory.EntityMappingCustomizer;
import org.hswebframework.web.crud.entity.factory.MapperEntityFactory;
import org.jetlinks.community.auth.entity.MenuBindEntity;
import org.jetlinks.community.auth.entity.OrganizationEntity;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.auth.entity.RoleGroupEntity;
import org.jetlinks.community.auth.entity.ThirdPartyUserBindEntity;
import org.jetlinks.community.auth.entity.UserDetailEntity;
import org.jetlinks.community.device.entity.DeviceInstanceEntity;
import org.jetlinks.community.device.entity.DeviceMetadataMappingEntity;
import org.jetlinks.community.device.entity.DeviceProductEntity;
import org.jetlinks.community.device.entity.DeviceTagEntity;
import org.jetlinks.community.device.entity.TransparentMessageCodecEntity;
import org.jetlinks.community.io.file.FileEntity;
import org.jetlinks.community.network.manager.entity.CertificateEntity;
import org.jetlinks.community.network.manager.entity.DeviceGatewayEntity;
import org.jetlinks.community.network.manager.entity.NetworkConfigEntity;
import org.jetlinks.community.notify.manager.entity.NotifyConfigEntity;
import org.jetlinks.community.notify.manager.entity.NotifyTemplateEntity;
import org.jetlinks.community.relation.entity.RelatedEntity;
import org.jetlinks.community.rule.engine.entity.AlarmConfigEntity;
import org.jetlinks.community.rule.engine.entity.AlarmHandleHistoryEntity;
import org.jetlinks.community.rule.engine.entity.AlarmRecordEntity;
import org.jetlinks.community.rule.engine.entity.AlarmRuleBindEntity;
import org.jetlinks.community.rule.engine.entity.RuleInstanceEntity;
import org.jetlinks.community.rule.engine.entity.SceneEntity;
import org.jetlinks.community.tenant.ext.*;
import org.jetlinks.community.things.impl.entity.PropertyMetricEntity;

/**
 * 把需要租户隔离的上游实体替换为租户扩展子类.
 * AutoDDL 通过 {@code EntityFactory.getInstanceType()} 解析子类, 自动补 tenant_id 列;
 * 联合索引由迁移脚本创建(自动DDL不建复合索引).
 *
 * @author tenant-manager
 * @since 2.11
 */
public class TenantEntityMappingCustomizer implements EntityMappingCustomizer {

    @Override
    public void custom(MapperEntityFactory factory) {
        // device-manager
        map(factory, DeviceInstanceEntity.class, TenantDeviceInstanceEntity.class);
        map(factory, DeviceProductEntity.class, TenantDeviceProductEntity.class);
        map(factory, DeviceTagEntity.class, TenantDeviceTagEntity.class);
        map(factory, DeviceMetadataMappingEntity.class, TenantDeviceMetadataMappingEntity.class);
        map(factory, TransparentMessageCodecEntity.class, TenantTransparentMessageCodecEntity.class);
        // network-manager
        map(factory, NetworkConfigEntity.class, TenantNetworkConfigEntity.class);
        map(factory, DeviceGatewayEntity.class, TenantDeviceGatewayEntity.class);
        map(factory, CertificateEntity.class, TenantCertificateEntity.class);
        // rule-engine-manager
        map(factory, RuleInstanceEntity.class, TenantRuleInstanceEntity.class);
        map(factory, SceneEntity.class, TenantSceneEntity.class);
        map(factory, AlarmConfigEntity.class, TenantAlarmConfigEntity.class);
        map(factory, AlarmRecordEntity.class, TenantAlarmRecordEntity.class);
        map(factory, AlarmHandleHistoryEntity.class, TenantAlarmHandleHistoryEntity.class);
        map(factory, AlarmRuleBindEntity.class, TenantAlarmRuleBindEntity.class);
        // notify-manager(订阅渠道/提供商为平台级, 不隔离, 见 D3)
        map(factory, NotifyConfigEntity.class, TenantNotifyConfigEntity.class);
        map(factory, NotifyTemplateEntity.class, TenantNotifyTemplateEntity.class);
        // authentication-manager(组织树降级为租户内部门树)
        map(factory, OrganizationEntity.class, TenantOrganizationEntity.class);
        map(factory, RoleEntity.class, TenantRoleEntity.class);
        map(factory, RoleGroupEntity.class, TenantRoleGroupEntity.class);
        map(factory, UserDetailEntity.class, TenantUserDetailEntity.class);
        map(factory, ThirdPartyUserBindEntity.class, TenantThirdPartyUserBindEntity.class);
        map(factory, MenuBindEntity.class, TenantMenuBindEntity.class);
        // components
        map(factory, FileEntity.class, TenantFileEntity.class);
        map(factory, PropertyMetricEntity.class, TenantPropertyMetricEntity.class);
        map(factory, RelatedEntity.class, TenantRelatedEntity.class);
    }

    private <T> void map(MapperEntityFactory factory, Class<T> source, Class<? extends T> target) {
        factory.addMapping(source, MapperEntityFactory.defaultMapper(target));
    }
}
