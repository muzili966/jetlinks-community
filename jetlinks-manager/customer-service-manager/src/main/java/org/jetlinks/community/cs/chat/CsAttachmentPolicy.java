package org.jetlinks.community.cs.chat;

import lombok.RequiredArgsConstructor;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.enums.CsChatMessageType;
import org.jetlinks.community.io.file.FileInfo;

import java.util.Locale;

/**
 * 附件规则: 按扩展名归类为图片 / 视频 / 文件, 各类有独立的大小上限; 不在白名单里的扩展名一律拒绝.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@RequiredArgsConstructor
public class CsAttachmentPolicy {

    public static final String ERROR_TYPE = "error.cs_chat_attachment_type_not_allowed";
    public static final String ERROR_SIZE = "error.cs_chat_attachment_too_large";

    private final CsProperties.Chat chat;

    public CsChatMessageType classify(String fileName) {
        String ext = extensionOf(fileName);
        if (chat.getImageExtensions().contains(ext)) {
            return CsChatMessageType.image;
        }
        if (chat.getVideoExtensions().contains(ext)) {
            return CsChatMessageType.video;
        }
        if (chat.getFileExtensions().contains(ext)) {
            return CsChatMessageType.file;
        }
        throw new ValidationException.NoStackTrace(ERROR_TYPE);
    }

    public long maxSize(CsChatMessageType type) {
        switch (type) {
            case image:
                return chat.getImageMaxSize().toBytes();
            case video:
                return chat.getVideoMaxSize().toBytes();
            default:
                return chat.getFileMaxSize().toBytes();
        }
    }

    public boolean allowsSize(CsChatMessageType type, long length) {
        return length <= maxSize(type);
    }

    public void assertSize(CsChatMessageType type, long length) {
        if (!allowsSize(type, length)) {
            throw new ValidationException.NoStackTrace(ERROR_SIZE);
        }
    }

    /**
     * 附件在消息里存相对路径而不是 {@link FileInfo#getAccessUrl()} 的绝对地址.
     * 绝对地址来自系统配置 paths.base-path, 是上传那一刻的值; 官网、控制台、内外网访问到的域名都可能与它不同,
     * 一旦不一致图片就加载不出来. 存相对路径由各端用自己的接口地址拼, 历史绝对地址各端按 http 前缀兼容.
     */
    public static String accessPath(FileInfo info) {
        String extension = info.getExtension();
        String path = "/file/" + info.getId() + (extension == null || extension.isBlank() ? "" : "." + extension);
        return info.accessKey().map(key -> path + "?accessKey=" + key).orElse(path);
    }

    /** 会话列表里的最后一条摘要 */
    public static String summaryOf(CsChatMessageType type, String fileName) {
        switch (type) {
            case image:
                return "[图片]";
            case video:
                return "[视频]";
            case file:
                return "[文件] " + (fileName == null ? "" : fileName);
            default:
                return "";
        }
    }

    static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
