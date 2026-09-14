package org.jetlinks.community.tenant.messaging;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.ReactiveAuthenticationManager;
import org.hswebframework.web.authorization.simple.SimpleAuthentication;
import org.jetlinks.community.tenant.context.TenantImpersonationAuthenticator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class ImpersonatingAuthenticationManagerTest {

    private final ReactiveAuthenticationManager delegate = mock(ReactiveAuthenticationManager.class);

    private final TenantImpersonationAuthenticator authenticator = mock(TenantImpersonationAuthenticator.class);

    private final ImpersonatingAuthenticationManager manager =
        new ImpersonatingAuthenticationManager(delegate, authenticator, "7");

    @Test
    void userIsResolvedToImpersonatedIdentity() {
        Authentication real = new SimpleAuthentication();
        Authentication derived = new SimpleAuthentication();
        when(delegate.getByUserId("admin")).thenReturn(Mono.just(real));
        when(authenticator.resolve(real, "7")).thenReturn(Mono.just(derived));

        StepVerifier
            .create(manager.getByUserId("admin"))
            .expectNext(derived)
            .verifyComplete();
    }

    @Test
    void unknownUserStaysEmpty() {
        when(delegate.getByUserId("ghost")).thenReturn(Mono.empty());

        StepVerifier
            .create(manager.getByUserId("ghost"))
            .verifyComplete();
        verifyNoInteractions(authenticator);
    }
}
