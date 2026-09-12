package org.jetlinks.community.auth.service;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import reactor.core.publisher.Mono;

/**
 * 用户详情查询的扩展点。
 * <p>
 * {@link UserDetailService#queryUserDetail} 使用 QueryHelper 原生联表查询,
 * 不经过 easy-orm 实体事件, 行级数据权限(如租户隔离)挂不上去。
 * 需要追加查询约束的模块(如 tenant-manager)实现本接口即可, 无需反向依赖。
 */
public interface UserDetailQueryCustomizer {

    /**
     * @param param 即将执行的查询参数, 可在其上追加条件
     * @return 处理后的查询参数
     */
    Mono<QueryParamEntity> customize(QueryParamEntity param);
}
