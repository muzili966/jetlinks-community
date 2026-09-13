package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 坐席发送卡片. params 由各卡片种类自行校验.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsCardSendRequest {

    @NotBlank(message = "请选择卡片种类")
    @Schema(description = "卡片种类, 如 link / renewal")
    private String kind;

    @Schema(description = "卡片参数; link: title / description / url / actionText, renewal: planId / months")
    private Map<String, Object> params;
}
