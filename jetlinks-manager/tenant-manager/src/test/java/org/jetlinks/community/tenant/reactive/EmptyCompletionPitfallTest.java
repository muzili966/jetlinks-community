package org.jetlinks.community.tenant.reactive;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 锁住一个在本模块里反复踩到的坑：{@code Mono<Void>} / 空流的「正常完成」会触发
 * {@code switchIfEmpty}，导致后备分支被误执行。
 * <p>
 * 真机上它造成过三种截然不同的故障，但根因是同一个：
 * <ul>
 *     <li>WebFilter 里过滤链跑两遍 → chunked 响应不终止 → 前端超时白屏</li>
 *     <li>权限校验通过后仍抛拒绝异常 → 租户管理员无法给自己的角色授权</li>
 *     <li>租户数据过滤后为空时落到无过滤查询 → <strong>数据越权</strong></li>
 * </ul>
 * 正确做法一律是：先把「有没有值」物化成 Optional/boolean，再分支。
 */
class EmptyCompletionPitfallTest {

    /** 错误写法：下游正常完成(空)也会触发 switchIfEmpty */
    @Test
    void switchIfEmptyIsTriggeredByNormalEmptyCompletion() {
        AtomicInteger fallback = new AtomicInteger();

        Mono<Void> action = Mono.empty();   // 模拟 chain.filter(exchange) 放行成功
        StepVerifier
            .create(Mono.just("auth")
                        .flatMap(a -> action)
                        .switchIfEmpty(Mono.fromRunnable(fallback::incrementAndGet)))
            .verifyComplete();

        // 上游明明有值(auth)、动作也执行了，后备分支仍被触发
        assertOne(fallback.get());
    }

    /** 正确写法：物化成 Optional 后分支，后备分支不再被误触发 */
    @Test
    void materializedOptionalAvoidsFallback() {
        AtomicInteger fallback = new AtomicInteger();

        Mono<Void> action = Mono.empty();
        StepVerifier
            .create(Mono.just("auth")
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.<String>empty())
                        .flatMap(opt -> opt.isPresent()
                            ? action
                            : Mono.fromRunnable(fallback::incrementAndGet)))
            .verifyComplete();

        assertZero(fallback.get());
    }

    /** 数据隔离场景：过滤后结果为空时，绝不能回退到无过滤查询 */
    @Test
    void emptyFilteredResultMustNotFallBackToUnfiltered() {
        AtomicInteger unfiltered = new AtomicInteger();
        Flux<String> allData = Flux.defer(() -> {
            unfiltered.incrementAndGet();
            return Flux.just("其他租户的数据");
        });

        StepVerifier
            .create(Mono.just("t001")
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.<String>empty())
                        .flatMapMany(opt -> opt.isPresent()
                            ? Flux.<String>empty()      // 本租户确实没有数据
                            : allData))
            .verifyComplete();

        assertZero(unfiltered.get());
    }

    private static void assertOne(int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(
            1, actual, "switchIfEmpty 应当被空完成触发——这正是坑本身");
    }

    private static void assertZero(int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(
            0, actual, "后备分支不应被触发");
    }
}
