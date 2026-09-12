/*
 * Copyright 2025 JetLinks https://www.jetlinks.cn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetlinks.community.logging.access;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.logging.events.AccessLoggerAfterEvent;
import org.jetlinks.core.utils.TopicUtils;
import org.jetlinks.community.logging.configuration.LoggingProperties;
import org.jetlinks.community.logging.utils.LoggingUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;

public class AccessLoggingTranslator {

    private final ApplicationEventPublisher eventPublisher;

    private final LoggingProperties properties;

    public AccessLoggingTranslator(ApplicationEventPublisher eventPublisher, LoggingProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @EventListener
    public void translate(AccessLoggerAfterEvent event) {

        for (String pathExclude : properties.getAccess().getPathExcludes()) {
            if (TopicUtils.match(pathExclude, event.getLogger().getUrl())) {
                return;
            }
        }
        SerializableAccessLog log = SerializableAccessLog.of(event.getLogger());

        // 在请求上下文里解析发起者与租户后再发布。
        // aop 侧以 publish(eventPublisher).contextWrite(请求上下文).subscribe() 订阅本事件的
        // async 链，因此这里的 currentReactive() 能拿到登录人；直接 publishEvent 则拿不到——
        // 真机现象: 日志 creatorId 全空, 租户过滤无从下手。
        event.async(
            Authentication
                .currentReactive()
                .doOnNext(auth -> {
                    log.setCreatorId(auth.getUser().getId());
                    auth.getDimensions()
                        .stream()
                        // "tenant" 为租户维度类型ID(tenant-manager 定义);
                        // 此处用字面量避免组件反向依赖 manager 模块
                        .filter(d -> d.getType() != null && "tenant".equals(d.getType().getId()))
                        .findFirst()
                        .ifPresent(d -> log.setTenantId(d.getId()));
                })
                // 匿名请求无认证, then 仍会执行发布
                .then(Mono.fromRunnable(() -> eventPublisher.publishEvent(log)))
        );
    }


}
