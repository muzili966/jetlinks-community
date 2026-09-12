package org.jetlinks.community.cs.lead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactKeysTest {

    @Test
    void phoneKeepsDigitsOnly() {
        assertEquals("13800138000", ContactKeys.normalizePhone("138-0013-8000"));
        assertEquals("13800138000", ContactKeys.normalizePhone(" 138 0013 8000 "));
    }

    @Test
    void chineseCountryCodeIsStripped() {
        assertEquals("13800138000", ContactKeys.normalizePhone("+86 13800138000"));
        assertEquals("13800138000", ContactKeys.normalizePhone("8613800138000"));
    }

    @Test
    void tooShortOrTooLongIsRejected() {
        assertNull(ContactKeys.normalizePhone("12345"));
        assertNull(ContactKeys.normalizePhone("123456789012345678901"));
        assertNull(ContactKeys.normalizePhone("abc"));
        assertNull(ContactKeys.normalizePhone(null));
    }

    @Test
    void landlineWithAreaCodeIsAccepted() {
        assertEquals("01088888888", ContactKeys.normalizePhone("010-8888 8888"));
    }

    @Test
    void wechatTrimsButKeepsCase() {
        assertEquals("Wx_Abc", ContactKeys.normalizeWechat("  Wx_Abc "));
        assertNull(ContactKeys.normalizeWechat("   "));
        assertNull(ContactKeys.normalizeWechat(null));
    }

    @Test
    void hasContactRequiresAtLeastOneValidKey() {
        assertTrue(ContactKeys.hasContact("13800138000", null));
        assertTrue(ContactKeys.hasContact(null, "wx1"));
        assertFalse(ContactKeys.hasContact("123", "  "));
        assertFalse(ContactKeys.hasContact(null, null));
    }
}
