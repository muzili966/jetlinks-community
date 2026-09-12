package org.jetlinks.community.cs.lead;

import org.hswebframework.web.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.jetlinks.community.cs.enums.CsLeadState.converted;
import static org.jetlinks.community.cs.enums.CsLeadState.following;
import static org.jetlinks.community.cs.enums.CsLeadState.invalid;
import static org.jetlinks.community.cs.enums.CsLeadState.pending;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsLeadStateMachineTest {

    @Test
    void pendingCanMoveForwardOrBeInvalidated() {
        assertTrue(CsLeadStateMachine.canTransit(pending, following));
        assertTrue(CsLeadStateMachine.canTransit(pending, converted));
        assertTrue(CsLeadStateMachine.canTransit(pending, invalid));
    }

    @Test
    void followingCannotGoBackToPending() {
        assertFalse(CsLeadStateMachine.canTransit(following, pending));
        assertTrue(CsLeadStateMachine.canTransit(following, converted));
        assertTrue(CsLeadStateMachine.canTransit(following, invalid));
    }

    @Test
    void convertedIsTerminal() {
        assertFalse(CsLeadStateMachine.canTransit(converted, pending));
        assertFalse(CsLeadStateMachine.canTransit(converted, following));
        assertFalse(CsLeadStateMachine.canTransit(converted, invalid));
    }

    @Test
    void invalidCanOnlyBeReactivated() {
        assertTrue(CsLeadStateMachine.canTransit(invalid, pending));
        assertFalse(CsLeadStateMachine.canTransit(invalid, following));
        assertFalse(CsLeadStateMachine.canTransit(invalid, converted));
    }

    @Test
    void sameOrNullStateIsNotATransition() {
        assertFalse(CsLeadStateMachine.canTransit(pending, pending));
        assertFalse(CsLeadStateMachine.canTransit(null, pending));
        assertFalse(CsLeadStateMachine.canTransit(pending, null));
    }

    @Test
    void assertTransitThrowsBusinessException() {
        assertThrows(BusinessException.class, () -> CsLeadStateMachine.assertTransit(converted, pending));
        assertDoesNotThrow(() -> CsLeadStateMachine.assertTransit(pending, following));
    }
}
