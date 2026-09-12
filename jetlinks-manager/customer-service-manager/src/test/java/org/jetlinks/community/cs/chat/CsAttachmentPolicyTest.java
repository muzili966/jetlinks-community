package org.jetlinks.community.cs.chat;

import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.enums.CsChatMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsAttachmentPolicyTest {

    private final CsAttachmentPolicy policy = new CsAttachmentPolicy(new CsProperties.Chat());

    @Test
    void classifiesByExtensionIgnoringCase() {
        assertEquals(CsChatMessageType.image, policy.classify("photo.PNG"));
        assertEquals(CsChatMessageType.image, policy.classify("a.b.webp"));
        assertEquals(CsChatMessageType.video, policy.classify("demo.mp4"));
        assertEquals(CsChatMessageType.file, policy.classify("报价.xlsx"));
        assertEquals(CsChatMessageType.file, policy.classify("合同.pdf"));
    }

    @Test
    void rejectsUnknownOrMissingExtension() {
        assertThrows(ValidationException.class, () -> policy.classify("virus.exe"));
        assertThrows(ValidationException.class, () -> policy.classify("noext"));
        assertThrows(ValidationException.class, () -> policy.classify(null));
    }

    @Test
    void sizeLimitDependsOnType() {
        CsProperties.Chat chat = new CsProperties.Chat();
        chat.setImageMaxSize(DataSize.ofKilobytes(10));
        chat.setFileMaxSize(DataSize.ofKilobytes(20));
        CsAttachmentPolicy small = new CsAttachmentPolicy(chat);
        assertTrue(small.allowsSize(CsChatMessageType.image, 10 * 1024));
        assertFalse(small.allowsSize(CsChatMessageType.image, 10 * 1024 + 1));
        assertTrue(small.allowsSize(CsChatMessageType.file, 20 * 1024));
        assertThrows(ValidationException.class, () -> small.assertSize(CsChatMessageType.file, 20 * 1024 + 1));
    }

    @Test
    void summaryShowsTypeAndFileName() {
        assertEquals("[图片]", CsAttachmentPolicy.summaryOf(CsChatMessageType.image, "a.png"));
        assertEquals("[视频]", CsAttachmentPolicy.summaryOf(CsChatMessageType.video, "a.mp4"));
        assertEquals("[文件] 报价.xlsx", CsAttachmentPolicy.summaryOf(CsChatMessageType.file, "报价.xlsx"));
        assertEquals("[文件] ", CsAttachmentPolicy.summaryOf(CsChatMessageType.file, null));
    }
}
