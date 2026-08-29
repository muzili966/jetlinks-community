package org.jetlinks.community.tenant.service;

import org.jetlinks.community.tenant.TenantConstants;
import org.jetlinks.community.tenant.TenantPlanConstants;
import org.jetlinks.community.tenant.entity.TenantEntity;
import org.jetlinks.community.tenant.entity.TenantPlanEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;

class TenantQuotaResolverTest {

    private static final String TENANT_A = "t001";
    private static final long FREE_LIMIT = 10L;
    private static final long STANDARD_LIMIT = 1000L;
    private static final long OVERRIDE_LIMIT = 6666L;

    private TenantService tenantService;
    private TenantQuotaResolver resolver;

    @BeforeEach
    void setup() {
        tenantService = Mockito.mock(TenantService.class);
        TenantPlanService planService = Mockito.mock(TenantPlanService.class);

        Mockito.when(planService.findById(anyString())).thenReturn(Mono.empty());
        Mockito.when(planService.findById(TenantPlanConstants.PLAN_FREE))
               .thenReturn(Mono.just(plan(FREE_LIMIT)));
        Mockito.when(planService.findById(TenantPlanConstants.PLAN_STANDARD))
               .thenReturn(Mono.just(plan(STANDARD_LIMIT)));

        resolver = new TenantQuotaResolver(tenantService, planService);
    }

    private TenantPlanEntity plan(long maxDevice) {
        TenantPlanEntity plan = new TenantPlanEntity();
        plan.setQuota(Collections.singletonMap(TenantConstants.QUOTA_MAX_DEVICE, maxDevice));
        return plan;
    }

    private void givenTenant(TenantEntity tenant) {
        tenant.setId(TENANT_A);
        Mockito.when(tenantService.findById(TENANT_A)).thenReturn(Mono.just(tenant));
    }

    private Mono<Optional<Long>> resolveDeviceQuota() {
        return resolver.resolve(TENANT_A, TenantConstants.QUOTA_MAX_DEVICE);
    }

    @Test
    void tenantOverrideBeatsPlan() {
        TenantEntity tenant = new TenantEntity();
        tenant.setPlanId(TenantPlanConstants.PLAN_STANDARD);
        tenant.setQuota(Collections.singletonMap(TenantConstants.QUOTA_MAX_DEVICE, OVERRIDE_LIMIT));
        givenTenant(tenant);

        StepVerifier.create(resolveDeviceQuota())
                    .expectNext(Optional.of(OVERRIDE_LIMIT))
                    .verifyComplete();
    }

    @Test
    void planQuotaUsedWhenNoOverride() {
        TenantEntity tenant = new TenantEntity();
        tenant.setPlanId(TenantPlanConstants.PLAN_STANDARD);
        givenTenant(tenant);

        StepVerifier.create(resolveDeviceQuota())
                    .expectNext(Optional.of(STANDARD_LIMIT))
                    .verifyComplete();
    }

    @Test
    void expiredSubscriptionDowngradesToFree() {
        TenantEntity tenant = new TenantEntity();
        tenant.setPlanId(TenantPlanConstants.PLAN_STANDARD);
        tenant.setSubscribeExpireTime(System.currentTimeMillis() - 1);
        givenTenant(tenant);

        StepVerifier.create(resolveDeviceQuota())
                    .expectNext(Optional.of(FREE_LIMIT))
                    .verifyComplete();
    }

    @Test
    void noPlanFallsBackToFree() {
        givenTenant(new TenantEntity());

        StepVerifier.create(resolveDeviceQuota())
                    .expectNext(Optional.of(FREE_LIMIT))
                    .verifyComplete();
    }

    @Test
    void unknownTenantUnlimited() {
        Mockito.when(tenantService.findById(TENANT_A)).thenReturn(Mono.empty());

        StepVerifier.create(resolveDeviceQuota())
                    .expectNext(Optional.empty())
                    .verifyComplete();
    }
}
