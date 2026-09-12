package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceCrudController;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.CsProperties;
import org.jetlinks.community.cs.entity.CsFaqEntity;
import org.jetlinks.community.cs.service.CsFaqService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 常见问题维护(客服端); 坐席工作台也用它做常用回复.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/cs/faq")
@Authorize
@Resource(id = CsConstants.RESOURCE_FAQ, name = "客服常见问题")
@AllArgsConstructor
@Getter
@Tag(name = "客服常见问题")
public class CsFaqController implements ReactiveServiceCrudController<CsFaqEntity, String> {

    private static final int QUICK_REPLY_LIMIT = 200;

    private final CsFaqService service;
    private final CsProperties properties;

    @Override
    public ReactiveCrudService<CsFaqEntity, String> getService() {
        return service;
    }

    @GetMapping("/_enabled")
    @QueryAction
    @Operation(summary = "启用中的常见问题(供工作台常用回复)")
    public Flux<CsFaqEntity> enabled() {
        return service.findEnabled(QUICK_REPLY_LIMIT);
    }
}
