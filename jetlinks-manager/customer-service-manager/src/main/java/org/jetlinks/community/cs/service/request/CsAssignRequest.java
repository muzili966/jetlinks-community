package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 指派线索负责人.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsAssignRequest {

    @NotBlank(message = "负责人不能为空")
    @Schema(description = "负责人用户ID")
    private String ownerId;
}
