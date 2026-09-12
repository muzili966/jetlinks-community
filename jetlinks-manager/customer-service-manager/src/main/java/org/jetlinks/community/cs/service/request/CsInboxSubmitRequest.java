package org.jetlinks.community.cs.service.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.lead.ContactKeys;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 官网留言提交.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Getter
@Setter
public class CsInboxSubmitRequest {

    static final int TRAIL_MAX_ITEMS = 50;
    static final int UTM_MAX_ITEMS = 10;

    @NotBlank(message = "称呼不能为空")
    @Size(max = 64)
    @Schema(description = "称呼")
    private String name;

    @Size(max = 32)
    @Schema(description = "手机号(与微信号至少填一项)")
    private String phone;

    @Size(max = 64)
    @Schema(description = "微信号(与手机号至少填一项)")
    private String wechat;

    @Size(max = 128)
    @Schema(description = "公司")
    private String company;

    @NotBlank(message = "留言内容不能为空")
    @Schema(description = "留言内容")
    private String content;

    @Size(max = 512)
    @Schema(description = "留言所在页面地址")
    private String sourcePage;

    @Size(max = 512)
    @Schema(description = "来源站点")
    private String referrer;

    @Schema(description = "来源渠道参数, 如 {\"utm_source\":\"baidu\"}")
    private Map<String, String> utm;

    @Schema(description = "浏览轨迹, 每项含 path 与 at")
    private List<Map<String, Object>> trail;

    @Size(max = 64)
    @Schema(description = "访客ID(浏览器本地生成)")
    private String visitorId;

    @Schema(description = "验证码标识(来自 /authorize/captcha/image)")
    private String verifyKey;

    @Schema(description = "验证码")
    private String verifyCode;

    /**
     * 注解校验之外的业务校验: 至少一种联系方式, 正文与附带数据不超限.
     */
    public void validate(int contentMaxLength) {
        if (!ContactKeys.hasContact(phone, wechat)) {
            throw new ValidationException.NoStackTrace("error.cs_inbox_contact_required");
        }
        if (content != null && content.length() > contentMaxLength) {
            throw new ValidationException.NoStackTrace("error.cs_inbox_content_too_long");
        }
        if (trail != null && trail.size() > TRAIL_MAX_ITEMS) {
            trail = trail.subList(trail.size() - TRAIL_MAX_ITEMS, trail.size());
        }
        if (utm != null && utm.size() > UTM_MAX_ITEMS) {
            throw new ValidationException.NoStackTrace("error.cs_inbox_utm_too_many");
        }
    }

    /**
     * 供验证码组件读取参数
     */
    public Optional<Object> captchaParameter(String parameterName) {
        if ("verifyKey".equals(parameterName)) {
            return Optional.ofNullable(verifyKey);
        }
        if ("verifyCode".equals(parameterName)) {
            return Optional.ofNullable(verifyCode);
        }
        return Optional.empty();
    }
}
