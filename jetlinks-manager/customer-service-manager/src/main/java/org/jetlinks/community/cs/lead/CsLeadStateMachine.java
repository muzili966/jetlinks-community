package org.jetlinks.community.cs.lead;

import org.hswebframework.web.exception.BusinessException;
import org.jetlinks.community.cs.enums.CsLeadState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.jetlinks.community.cs.enums.CsLeadState.converted;
import static org.jetlinks.community.cs.enums.CsLeadState.following;
import static org.jetlinks.community.cs.enums.CsLeadState.invalid;
import static org.jetlinks.community.cs.enums.CsLeadState.pending;

/**
 * 线索状态流转规则: 已转化是终态; 无效可重新激活.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public final class CsLeadStateMachine {

    private static final Map<CsLeadState, Set<CsLeadState>> TRANSITIONS = new EnumMap<>(CsLeadState.class);

    static {
        TRANSITIONS.put(pending, EnumSet.of(following, converted, invalid));
        TRANSITIONS.put(following, EnumSet.of(converted, invalid));
        TRANSITIONS.put(invalid, EnumSet.of(pending));
        TRANSITIONS.put(converted, EnumSet.noneOf(CsLeadState.class));
    }

    private CsLeadStateMachine() {
    }

    public static boolean canTransit(CsLeadState from, CsLeadState to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(CsLeadState.class)).contains(to);
    }

    public static void assertTransit(CsLeadState from, CsLeadState to) {
        if (!canTransit(from, to)) {
            throw new BusinessException("error.cs_lead_state_transition", 400,
                                        from == null ? "" : from.getText(),
                                        to == null ? "" : to.getText());
        }
    }
}
