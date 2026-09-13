package org.jetlinks.community.pay.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.community.pay.spi.PayCapability;
import org.jetlinks.community.pay.spi.PayChannelProvider;

import java.util.Set;

/**
 * 对外展示的渠道, 不带任何配置与凭证.
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@AllArgsConstructor
public class PayChannelView {

    @Schema(description = "渠道ID")
    private final String id;

    @Schema(description = "名称")
    private final String name;

    @Schema(description = "能力")
    private final Set<PayCapability> capabilities;

    public static PayChannelView of(PayChannelProvider provider) {
        return new PayChannelView(provider.getId(), provider.getName(), provider.getCapabilities());
    }
}
