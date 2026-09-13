package org.jetlinks.community.cs.card;

import org.jetlinks.community.cs.entity.CsSessionEntity;
import reactor.core.publisher.Mono;

/**
 * 卡片种类扩展点: 一种卡片一个实现, 以 Spring Bean 注册.
 * <p>
 * 契约:
 * <ul>
 *     <li>{@link #getKind()} 写进消息内容并被三端识别, 是稳定契约</li>
 *     <li>卡片内容在服务端生成: 金额、支付链接这类字段不能直接取坐席输入</li>
 *     <li>生成过程可以创建业务数据(如续费订单), 与消息写入同一事务</li>
 * </ul>
 *
 * @author customer-service-manager
 * @since 2.11
 */
public interface CsCardProvider {

    String getKind();

    String getName();

    /**
     * 这个会话能不能发这种卡片; 工作台据此决定展示哪些选项
     */
    boolean supports(CsSessionEntity session);

    Mono<CsCardDraft> build(CsCardContext context);
}
