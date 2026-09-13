package org.jetlinks.community.pay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付配置.
 *
 * <pre>{@code
 * pay:
 *   enabled: true
 *   order-ttl: 24h                  # 支付单有效期, 过期未付自动关闭
 *   expire-check-interval: 1m
 *   notify-base-url: https://api.example.com   # 网关能访问到的平台地址, 用来拼回调 /pay/notify/{channel}
 *   channels:
 *     offline:
 *       enabled: true               # 渠道开关; 未配置的渠道默认启用
 *   offline:
 *     instructions:                 # 线下转账页展示给付款人的信息
 *       收款户名: 天马物联科技有限公司
 *   sandbox:
 *     enabled: false                # 开发联调用, 生产不要打开
 *     secret: ${PAY_SANDBOX_SECRET}
 * }</pre>
 *
 * @author pay-manager
 * @since 2.11
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pay")
public class PayProperties {

    private boolean enabled = true;

    private Duration orderTtl = Duration.ofHours(24);

    private Duration expireCheckInterval = Duration.ofMinutes(1);

    /**
     * 网关回调时能访问到的平台根地址. 为空时渠道拿不到回调地址, 只能用线下确认或沙箱.
     */
    private String notifyBaseUrl;

    private Map<String, Channel> channels = new HashMap<>();

    private Offline offline = new Offline();

    private Sandbox sandbox = new Sandbox();

    public boolean isChannelEnabled(String channelId) {
        Channel channel = channels.get(channelId);
        return channel == null || channel.isEnabled();
    }

    @Getter
    @Setter
    public static class Channel {

        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Offline {

        /**
         * 付款人看到的转账说明; 真实收款账户由部署侧配置, 源码里不写
         */
        private Map<String, String> instructions = defaultInstructions();

        /** 逐项放入: 展示顺序就是配置顺序, Map.of 的迭代顺序不固定 */
        static Map<String, String> defaultInstructions() {
            Map<String, String> instructions = new LinkedHashMap<>();
            instructions.put("付款方式", "对公转账");
            instructions.put("说明", "请联系客服获取收款账户，转账时在备注中填写订单号，平台确认到账后自动生效");
            return instructions;
        }
    }

    @Getter
    @Setter
    public static class Sandbox {

        private boolean enabled = false;

        /**
         * 沙箱回调签名密钥, 只在服务端使用; 开启沙箱但未配置时启动失败
         */
        private String secret;

        /**
         * 回调时间戳允许的最大偏差, 防重放
         */
        private Duration notifyMaxAge = Duration.ofMinutes(5);
    }
}
