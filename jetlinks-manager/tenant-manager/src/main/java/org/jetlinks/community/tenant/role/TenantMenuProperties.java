package org.jetlinks.community.tenant.role;

import org.hswebframework.web.authorization.Authentication;
import org.jetlinks.community.auth.configuration.MenuProperties;
import org.jetlinks.community.tenant.context.TenantImpersonation;

/**
 * 代理态下关闭「按用户名放行全部菜单」。
 * <p>
 * admin 的菜单走 {@link MenuProperties#isAllowAllMenu} 的用户名白名单分支，与维度无关；
 * 代理态的降权身份用户名仍是 admin（审计需要），不关掉的话租户视图照样拿到全部菜单。
 * 关掉后 DefaultMenuService 按维度查 s_menu_bind，得到的就是租户管理员角色的菜单。
 * <p>
 * 该方法同步执行、拿不到 Reactor Context，只能凭身份上的代理标记判断。
 *
 * @author tenant-manager
 * @since 2.11
 */
public class TenantMenuProperties extends MenuProperties {

    @Override
    public boolean isAllowAllMenu(Authentication auth) {
        return !TenantImpersonation.isImpersonated(auth) && super.isAllowAllMenu(auth);
    }
}
