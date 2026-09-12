package org.jetlinks.community.cs.service.request;

import org.hswebframework.web.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsChatMessageRequestTest {

    private CsChatMessageRequest request(String content) {
        CsChatMessageRequest request = new CsChatMessageRequest();
        request.setContent(content);
        return request;
    }

    @Test
    void stripsWhitespace() {
        assertEquals("你好", request("  你好\n").validated(10));
    }

    @Test
    void rejectsBlankAndTooLong() {
        assertThrows(ValidationException.class, () -> request("   ").validated(10));
        assertThrows(ValidationException.class, () -> request(null).validated(10));
        assertThrows(ValidationException.class, () -> request("12345678901").validated(10));
        assertEquals("1234567890", request("1234567890").validated(10));
    }

    @Test
    void contactRequestNeedsNameOrContact() {
        CsSessionContactRequest empty = new CsSessionContactRequest();
        assertThrows(ValidationException.class, empty::validate);
        empty.setVisitorContact("13800000000");
        empty.validate();
    }
}
