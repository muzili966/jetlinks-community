package org.jetlinks.community.tenant.role;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.hswebframework.web.authorization.simple.SimpleUser;
import org.jetlinks.community.tenant.TenantConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantMenuPropertiesTest {

    private final TenantMenuProperties properties = new TenantMenuProperties();

    @Test
    void adminKeepsAllMenusOutsideTenantView() {
        assertTrue(properties.isAllowAllMenu(user("admin", false)));
    }

    @Test
    void impersonatedAdminFallsBackToGrantedMenus() {
        assertFalse(properties.isAllowAllMenu(user("admin", true)));
    }

    @Test
    void normalUserNeverGetsAllMenus() {
        assertFalse(properties.isAllowAllMenu(user("cslgdz_admin", false)));
    }

    private Authentication user(String username, boolean impersonated) {
        SimpleAuthentication auth = new SimpleAuthentication();
        auth.setUser(SimpleUser.builder().id("id-" + username).username(username).name(username).build());
        if (impersonated) {
            auth.setAttribute(TenantConstants.IMPERSONATOR_ATTRIBUTE, "id-" + username);
        }
        return auth;
    }
}
