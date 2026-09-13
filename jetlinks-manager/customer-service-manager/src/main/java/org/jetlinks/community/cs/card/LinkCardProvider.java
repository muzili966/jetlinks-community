package org.jetlinks.community.cs.card;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 通用链接卡片: 文档、方案、活动页. 链接只允许 http/https, 可按配置限定域名.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class LinkCardProvider implements CsCardProvider {

    public static final String KIND = "link";
    static final int TITLE_MAX_LENGTH = 64;
    static final int DESCRIPTION_MAX_LENGTH = 256;
    static final int URL_MAX_LENGTH = 1024;
    static final int ACTION_TEXT_MAX_LENGTH = 16;
    static final String DEFAULT_ACTION_TEXT = "查看详情";

    private final CsProperties properties;

    @Override
    public String getKind() {
        return KIND;
    }

    @Override
    public String getName() {
        return "链接卡片";
    }

    @Override
    public boolean supports(CsSessionEntity session) {
        return true;
    }

    @Override
    public Mono<CsCardDraft> build(CsCardContext context) {
        return Mono.fromCallable(() -> CsCardDraft.of(cardOf(context, properties.getChat().getCardLinkHosts())));
    }

    static CsCard cardOf(CsCardContext context, Set<String> allowedHosts) {
        CsCard card = new CsCard();
        card.setKind(KIND);
        card.setTitle(required(context.text("title"), TITLE_MAX_LENGTH, "title"));
        card.setDescription(optional(context.text("description"), DESCRIPTION_MAX_LENGTH, "description"));
        String url = required(context.text("url"), URL_MAX_LENGTH, "url");
        assertSafeUrl(url, allowedHosts);
        String actionText = optional(context.text("actionText"), ACTION_TEXT_MAX_LENGTH, "actionText");
        card.setAction(new CsCard.Action(CsCard.Action.TYPE_URL, actionText == null ? DEFAULT_ACTION_TEXT : actionText, url));
        return card;
    }

    /**
     * 只放行 http/https: javascript:、data: 这类链接在访客浏览器里就是脚本注入
     */
    static void assertSafeUrl(String url, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException.NoStackTrace("error.cs_card_link_url_invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
            throw new ValidationException.NoStackTrace("error.cs_card_link_url_invalid");
        }
        if (allowedHosts != null && !allowedHosts.isEmpty() && !hostAllowed(uri.getHost(), allowedHosts)) {
            throw new ValidationException.NoStackTrace("error.cs_card_link_host_not_allowed", uri.getHost());
        }
    }

    static boolean hostAllowed(String host, Set<String> allowedHosts) {
        String target = host.toLowerCase(Locale.ROOT);
        return allowedHosts
            .stream()
            .map(allowed -> allowed.toLowerCase(Locale.ROOT).strip())
            .anyMatch(allowed -> target.equals(allowed) || target.endsWith("." + allowed));
    }

    private static String required(String value, int max, String name) {
        if (value == null || value.isEmpty()) {
            throw new ValidationException.NoStackTrace("error.cs_card_param_required", name);
        }
        return optional(value, max, name);
    }

    private static String optional(String value, int max, String name) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() > max) {
            throw new ValidationException.NoStackTrace("error.cs_card_param_too_long", name);
        }
        return value;
    }
}
