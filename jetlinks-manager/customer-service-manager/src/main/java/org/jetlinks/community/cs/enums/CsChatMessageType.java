package org.jetlinks.community.cs.enums;

import org.hswebframework.web.dict.EnumDict;

/**
 * 会话消息类型: 附件类型 content 存文件访问地址; 卡片类型 content 存卡片 JSON.
 * 常量 text 与 EnumDict 的 text 字段同名, 所以这里不用 Lombok 生成字段.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public enum CsChatMessageType implements EnumDict<String> {

    text("文本"),
    image("图片"),
    video("视频"),
    file("文件"),
    card("卡片");

    private final String label;

    CsChatMessageType(String label) {
        this.label = label;
    }

    @Override
    public String getValue() {
        return name();
    }

    @Override
    public String getText() {
        return label;
    }

    /** 图片、视频、文件才是附件; 卡片的 content 是 JSON, 不是文件地址 */
    public boolean isAttachment() {
        return this == image || this == video || this == file;
    }
}
