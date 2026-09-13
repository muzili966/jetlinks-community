package org.jetlinks.community.cs.card;

import com.alibaba.fastjson.JSON;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.jetlinks.community.tenant.service.TenantRenewal;
import org.jetlinks.community.tenant.service.TenantRenewalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsCardTest {

    private CsCardContext link(String title, String url) {
        Map<String, Object> params = new HashMap<>();
        params.put("title", title);
        params.put("url", url);
        return new CsCardContext(new CsSessionEntity(), params, "u1");
    }

    @Test
    void linkCardBuildsUrlAction() {
        CsCard card = LinkCardProvider.cardOf(link(" 私有化部署方案 ", "https://docs.example.com/deploy"), Set.of());
        assertEquals(LinkCardProvider.KIND, card.getKind());
        assertEquals("私有化部署方案", card.getTitle());
        assertEquals(CsCard.Action.TYPE_URL, card.getAction().getType());
        assertEquals(LinkCardProvider.DEFAULT_ACTION_TEXT, card.getAction().getText());
        assertEquals("https://docs.example.com/deploy", card.getAction().getTarget());
    }

    @Test
    void linkCardRejectsScriptAndMalformedUrls() {
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x", "javascript:alert(1)"), Set.of()));
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x", "data:text/html,hi"), Set.of()));
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x", "//no-scheme.com"), Set.of()));
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x", "http://bad url"), Set.of()));
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link(" ", "https://a.com"), Set.of()));
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x".repeat(65), "https://a.com"), Set.of()));
    }

    @Test
    void linkHostAllowlistMatchesDomainAndSubdomainsOnly() {
        Set<String> allowed = Set.of("tianma-iot.com");
        LinkCardProvider.cardOf(link("x", "https://tianma-iot.com/a"), allowed);
        LinkCardProvider.cardOf(link("x", "https://docs.TIANMA-IOT.com/a"), allowed);
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x", "https://eviltianma-iot.com"), allowed));
        assertThrows(ValidationException.class, () -> LinkCardProvider.cardOf(link("x", "https://tianma-iot.com.evil.cn"), allowed));
    }

    private TenantRenewal renewal() {
        return TenantRenewal.builder()
            .tenantId("t1").tenantName("天马科技").planName("标准版").months(12).totalAmount(21600)
            .currentExpireTime(null).expireTimeAfterPreview(1_900_000_000_000L)
            .tenantOrderId("o1").payOrderId("p1").subject("标准版 续费 12 个月").payAmount(2_160_000L).payExpireAt(1_800_000_000_000L)
            .build();
    }

    @Test
    void renewalCardLinksToCheckoutAndTracksPayOrder() {
        CsCardDraft draft = RenewalCardProvider.draftOf(renewal());
        assertEquals(RenewalCardProvider.REF_TYPE_PAY_ORDER, draft.getRefType());
        assertEquals("p1", draft.getRefId());
        assertEquals("pending", draft.getRefStatus());

        CsCard card = draft.getCard();
        assertEquals("续费 标准版", card.getTitle());
        assertEquals("¥21,600.00", card.getAmountText());
        assertEquals(CsCard.Action.TYPE_ROUTE, card.getAction().getType());
        assertEquals("/pay/checkout/p1", card.getAction().getTarget());
        assertEquals(1_800_000_000_000L, card.getExpireAt());
        CsCard.Field current = card.getFields().get(2);
        assertEquals(CsCard.Field.TYPE_DATETIME, current.getType());
        assertNull(current.getValue(), "未订阅时不能显示成 1970 年");
        assertEquals("1900000000000", card.getFields().get(3).getValue());

        // 存进消息的是 JSON, 三端据此渲染
        assertEquals("续费 标准版", JSON.parseObject(JSON.toJSONString(card)).getString("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void renewalCardOnlyForTenantSessionsWhenTenantModuleIsOn() {
        ObjectProvider<TenantRenewalService> missing = mock(ObjectProvider.class);
        ObjectProvider<TenantRenewalService> present = mock(ObjectProvider.class);
        when(present.getIfAvailable()).thenReturn(mock(TenantRenewalService.class));

        CsSessionEntity tenantSession = new CsSessionEntity();
        tenantSession.setTenantId("t1");
        CsSessionEntity visitorSession = new CsSessionEntity();

        assertTrue(new RenewalCardProvider(present).supports(tenantSession));
        assertFalse(new RenewalCardProvider(present).supports(visitorSession), "官网匿名访客没有租户, 不能续费");
        assertFalse(new RenewalCardProvider(missing).supports(tenantSession), "租户模块关闭时不可用");
    }

    static class NamedProvider implements CsCardProvider {
        private final String kind;
        private final boolean supported;

        NamedProvider(String kind, boolean supported) {
            this.kind = kind;
            this.supported = supported;
        }

        @Override
        public String getKind() {
            return kind;
        }

        @Override
        public String getName() {
            return kind;
        }

        @Override
        public boolean supports(CsSessionEntity session) {
            return supported;
        }

        @Override
        public Mono<CsCardDraft> build(CsCardContext context) {
            return Mono.empty();
        }
    }

    @Test
    void registryRejectsDuplicateKinds() {
        assertThrows(IllegalStateException.class,
                     () -> CsCardRegistry.build(List.of(new NamedProvider("link", true), new NamedProvider("link", true))));
    }

    @Test
    void contextParsesIntegerParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("months", "6");
        params.put("n", 3);
        params.put("bad", "abc");
        CsCardContext context = new CsCardContext(new CsSessionEntity(), params, "u1");
        assertEquals(6, context.integer("months", 12));
        assertEquals(3, context.integer("n", 12));
        assertEquals(12, context.integer("missing", 12));
        assertThrows(ValidationException.class, () -> context.integer("bad", 12));
    }
}
