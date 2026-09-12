package org.jetlinks.community.cs.chat;

import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.cs.enums.CsSessionState;

/**
 * 会话状态规则: 排队 → 接待 → 结束; 结束是终态, 访客再次咨询新开会话.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public final class CsSessionStateMachine {

    private CsSessionStateMachine() {
    }

    public static boolean canAccept(CsSessionState state) {
        return state == CsSessionState.queued;
    }

    public static boolean canTransfer(CsSessionState state) {
        return state == CsSessionState.active;
    }

    public static boolean canClose(CsSessionState state) {
        return state == CsSessionState.queued || state == CsSessionState.active;
    }

    public static boolean canChat(CsSessionState state) {
        return state == CsSessionState.queued || state == CsSessionState.active;
    }

    public static void assertAccept(CsSessionState state) {
        if (!canAccept(state)) {
            throw new BusinessException("error.cs_session_not_queued", 400);
        }
    }

    public static void assertTransfer(CsSessionState state) {
        if (!canTransfer(state)) {
            throw new BusinessException("error.cs_session_not_active", 400);
        }
    }

    public static void assertClose(CsSessionState state) {
        if (!canClose(state)) {
            throw new BusinessException("error.cs_session_already_closed", 400);
        }
    }

    public static void assertChat(CsSessionState state) {
        if (!canChat(state)) {
            throw new BusinessException("error.cs_session_already_closed", 400);
        }
    }
}
