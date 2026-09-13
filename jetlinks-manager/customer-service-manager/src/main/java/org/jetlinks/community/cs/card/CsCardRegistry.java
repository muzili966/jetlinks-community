package org.jetlinks.community.cs.card;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.entity.CsSessionEntity;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 卡片种类注册表. 首次使用时解析, 重复种类启动失败.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public class CsCardRegistry {

    private final ObjectProvider<CsCardProvider> providers;
    private volatile Map<String, CsCardProvider> index;

    public CsCardRegistry(ObjectProvider<CsCardProvider> providers) {
        this.providers = providers;
    }

    public Optional<CsCardProvider> find(String kind) {
        return Optional.ofNullable(kind == null ? null : index().get(kind));
    }

    public CsCardProvider required(String kind) {
        return find(kind).orElseThrow(() -> new ValidationException.NoStackTrace("error.cs_card_kind_unsupported", kind));
    }

    public List<CsCardProvider> available(CsSessionEntity session) {
        return index().values().stream().filter(provider -> provider.supports(session)).toList();
    }

    private Map<String, CsCardProvider> index() {
        Map<String, CsCardProvider> resolved = index;
        if (resolved == null) {
            synchronized (this) {
                if (index == null) {
                    index = build(providers.orderedStream().toList());
                }
                resolved = index;
            }
        }
        return resolved;
    }

    static Map<String, CsCardProvider> build(List<CsCardProvider> list) {
        Map<String, CsCardProvider> result = new LinkedHashMap<>();
        for (CsCardProvider provider : list) {
            CsCardProvider exists = result.putIfAbsent(provider.getKind(), provider);
            if (exists != null) {
                throw new IllegalStateException(String.format(
                    "duplicate customer-service card kind [%s]: %s and %s",
                    provider.getKind(), exists.getClass().getName(), provider.getClass().getName()));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
