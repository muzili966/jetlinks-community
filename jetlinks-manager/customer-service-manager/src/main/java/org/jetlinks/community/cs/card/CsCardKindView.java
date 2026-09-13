package org.jetlinks.community.cs.card;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 可发送的卡片种类.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class CsCardKindView {

    @Schema(description = "种类")
    private final String kind;

    @Schema(description = "名称")
    private final String name;

    public static CsCardKindView of(CsCardProvider provider) {
        return new CsCardKindView(provider.getKind(), provider.getName());
    }
}
