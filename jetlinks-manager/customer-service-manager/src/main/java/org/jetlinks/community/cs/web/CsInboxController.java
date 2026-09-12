package org.jetlinks.community.cs.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.SaveAction;
import org.hswebframework.web.crud.service.ReactiveCrudService;
import org.hswebframework.web.crud.web.reactive.ReactiveServiceQueryController;
import org.jetlinks.community.cs.CsConstants;
import org.jetlinks.community.cs.entity.CsInboxMessageEntity;
import org.jetlinks.community.cs.service.CsInboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 留言箱(客服端).
 *
 * @author customer-service-manager
 * @since 2.11
 */
@ConditionalOnProperty(prefix = "customer-service", name = "enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/cs/inbox")
@Authorize
@Resource(id = CsConstants.RESOURCE_INBOX, name = "客服留言")
@AllArgsConstructor
@Getter
@Tag(name = "客服留言箱")
public class CsInboxController implements ReactiveServiceQueryController<CsInboxMessageEntity, String> {

    private final CsInboxService service;

    @Override
    public ReactiveCrudService<CsInboxMessageEntity, String> getService() {
        return service;
    }

    @PostMapping("/_read")
    @SaveAction
    @Operation(summary = "标记留言已读")
    public Mono<Integer> markRead(@RequestBody Mono<List<String>> ids) {
        return ids.flatMap(service::markRead);
    }

    @GetMapping("/_unread-count")
    @QueryAction
    @Operation(summary = "未读留言数")
    public Mono<Integer> unreadCount() {
        return service.countUnread();
    }
}
