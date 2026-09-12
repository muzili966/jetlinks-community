package org.jetlinks.community.cs.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.cs.entity.CsFaqEntity;

/**
 * 对访客暴露的常见问题, 不带维护字段.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class CsFaqView {

    @Schema(description = "ID")
    private final String id;

    @Schema(description = "问题")
    private final String question;

    @Schema(description = "答案")
    private final String answer;

    @Schema(description = "分类")
    private final String category;

    public static CsFaqView of(CsFaqEntity entity) {
        return new CsFaqView(entity.getId(), entity.getQuestion(), entity.getAnswer(), entity.getCategory());
    }
}
