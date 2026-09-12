package org.jetlinks.community.cs.role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.jetlinks.community.auth.entity.RoleEntity;
import org.jetlinks.community.cs.CsProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import reactor.core.publisher.Mono;

/**
 * 启动时确保「客服」「客服主管」两个角色存在. 只建角色不授权:
 * 菜单与按钮权限在「菜单管理」导入 docs/menu-cs.json 后, 于角色管理中勾选.
 *
 * @author customer-service-manager
 * @since 2.11
 */
@Slf4j
@RequiredArgsConstructor
public class CsRoleInitializer {

    static final String AGENT_ROLE_NAME = "客服";
    static final String SUPERVISOR_ROLE_NAME = "客服主管";

    private final CsProperties properties;
    private final ReactiveRepository<RoleEntity, String> roleRepository;

    @EventListener
    public void start(ApplicationReadyEvent event) {
        ensureRole(properties.getAgentRoleId(), AGENT_ROLE_NAME, "处理官网留言与线索跟进")
            .then(ensureRole(properties.getSupervisorRoleId(), SUPERVISOR_ROLE_NAME, "可指派线索并查看客服统计"))
            .subscribe(ignore -> {
                       },
                       err -> log.error("initialize customer-service roles failed", err));
    }

    private Mono<Void> ensureRole(String roleId, String name, String description) {
        return roleRepository
            .findById(roleId)
            .switchIfEmpty(Mono.defer(() -> {
                RoleEntity role = roleRepository.newInstanceNow();
                role.setId(roleId);
                role.setName(name);
                role.setDescription(description);
                return roleRepository
                    .insert(role)
                    .doOnSuccess(ignore -> log.info("created customer-service role [{}] {}", roleId, name))
                    .thenReturn(role);
            }))
            .then();
    }
}
