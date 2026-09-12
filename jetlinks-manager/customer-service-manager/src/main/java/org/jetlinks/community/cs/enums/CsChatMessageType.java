package org.jetlinks.community.cs.enums;

import org.hswebframework.web.dict.EnumDict;

/**
 * 会话消息类型: 文本之外的类型, content 存文件访问地址.
 * 常量 text 与 EnumDict 的 text 字段同名, 所以这里不用 Lombok 生成字段.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public enum CsChatMessageType implements EnumDict<String> {

    text("文本"),
    image("图片"),
    video("视频"),
    file("文件");

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

    public boolean isAttachment() {
        return this != text;
    }
}
