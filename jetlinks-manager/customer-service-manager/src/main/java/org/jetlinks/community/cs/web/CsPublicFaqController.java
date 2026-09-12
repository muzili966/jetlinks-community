package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.service.CsFaqService;
import org.jetlinks.community.cs.web.response.CsFaqView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 官网聊天窗读取的常见问题(匿名, 只暴露问题与答案).
 *
 * @author customer-service-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping(CsConstants.PUBLIC_PATH + "/faq")
@Authorize(ignore = true)
@RequiredArgsConstructor
@Tag(name = "客服常见问题(访客)")
public class CsPublicFaqController {

    private final CsFaqService service;
    private final CsProperties properties;

    @GetMapping
    @Operation(summary = "启用中的常见问题")
    public Flux<CsFaqView> list() {
        return service.findEnabled(properties.getChat().getFaqLimit()).map(CsFaqView::of);
    }

    @PostMapping("/{id}/_hit")
    @Operation(summary = "访客点开了某个问题(计数)")
    public Mono<Void> hit(@PathVariable String id) {
        return service.hit(id);
    }
}
