package org.jetlinks.community.cs.service.request;

import org.hswebframework.web.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsInboxSubmitRequestTest {

    private static final int MAX = 20;

    private CsInboxSubmitRequest request(String phone, String wechat, String content) {
        CsInboxSubmitRequest request = new CsInboxSubmitRequest();
        request.setName("张三");
        request.setPhone(phone);
        request.setWechat(wechat);
        request.setContent(content);
        return request;
    }

    @Test
    void requiresAtLeastOneContact() {
        assertThrows(ValidationException.class, () -> request(null, null, "hi").validate(MAX));
        assertThrows(ValidationException.class, () -> request("123", "  ", "hi").validate(MAX));
        assertDoesNotThrow(() -> request(null, "wx", "hi").validate(MAX));
        assertDoesNotThrow(() -> request("13800138000", null, "hi").validate(MAX));
    }

    @Test
    void rejectsContentOverLimit() {
        assertThrows(ValidationException.class, () -> request("13800138000", null, "a".repeat(MAX + 1)).validate(MAX));
        assertDoesNotThrow(() -> request("13800138000", null, "a".repeat(MAX)).validate(MAX));
    }

    @Test
    void trailIsTrimmedToLatestItems() {
        CsInboxSubmitRequest request = request("13800138000", null, "hi");
        List<Map<String, Object>> trail = new ArrayList<>();
        for (int i = 0; i < CsInboxSubmitRequest.TRAIL_MAX_ITEMS + 5; i++) {
            trail.add(Collections.singletonMap("path", "/p" + i));
        }
        request.setTrail(trail);
        request.validate(MAX);
        assertEquals(CsInboxSubmitRequest.TRAIL_MAX_ITEMS, request.getTrail().size());
        assertEquals("/p5", request.getTrail().get(0).get("path"));
    }

    @Test
    void captchaParametersAreExposedByName() {
        CsInboxSubmitRequest request = request("13800138000", null, "hi");
        request.setVerifyKey("k");
        request.setVerifyCode("c");
        assertEquals(Optional.of("k"), request.captchaParameter("verifyKey"));
        assertEquals(Optional.of("c"), request.captchaParameter("verifyCode"));
        assertEquals(Optional.empty(), request.captchaParameter("other"));
    }
}
