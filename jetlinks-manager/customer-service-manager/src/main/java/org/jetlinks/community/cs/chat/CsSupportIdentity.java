package org.jetlinks.community.cs.chat;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 控制台用户发起会话时自动登记的身份: 登录账号 + 联系方式 + 所属租户.
 * 坐席在工作台直接看到是哪个租户的谁在问, 不用再让对方自报家门.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class CsSupportIdentity {

    private final String userId;
    private final String userName;
    private final String telephone;
    private final String tenantId;
    private final String tenantName;

    public static CsSupportIdentity of(String userId, String userName) {
        return new CsSupportIdentity(userId, userName, null, null, null);
    }

    public CsSupportIdentity withTelephone(String phone) {
        return new CsSupportIdentity(userId, userName, phone, tenantId, tenantName);
    }

    public CsSupportIdentity withTenant(String id, String name) {
        return new CsSupportIdentity(userId, userName, telephone, id, name);
    }
}
