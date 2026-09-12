package org.jetlinks.community.cs.chat;

import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.cs.enums.CsSessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsSessionStateMachineTest {

    @Test
    void onlyQueuedCanBeAccepted() {
        assertTrue(CsSessionStateMachine.canAccept(CsSessionState.queued));
        assertFalse(CsSessionStateMachine.canAccept(CsSessionState.active));
        assertFalse(CsSessionStateMachine.canAccept(CsSessionState.closed));
        BusinessException error = assertThrows(BusinessException.class,
                                               () -> CsSessionStateMachine.assertAccept(CsSessionState.active));
        assertEquals("error.cs_session_not_queued", error.getMessage());
    }

    @Test
    void onlyActiveCanBeTransferred() {
        assertTrue(CsSessionStateMachine.canTransfer(CsSessionState.active));
        assertFalse(CsSessionStateMachine.canTransfer(CsSessionState.queued));
        assertThrows(BusinessException.class, () -> CsSessionStateMachine.assertTransfer(CsSessionState.queued));
    }

    @Test
    void closedIsTerminal() {
        assertTrue(CsSessionStateMachine.canClose(CsSessionState.queued));
        assertTrue(CsSessionStateMachine.canClose(CsSessionState.active));
        assertFalse(CsSessionStateMachine.canClose(CsSessionState.closed));
        assertFalse(CsSessionStateMachine.canChat(CsSessionState.closed));
        assertThrows(BusinessException.class, () -> CsSessionStateMachine.assertChat(CsSessionState.closed));
        assertDoesNotThrow(() -> CsSessionStateMachine.assertChat(CsSessionState.queued));
    }
}
