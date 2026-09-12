package org.jetlinks.community.cs.chat;

import org.jetlinks.community.cs.entity.CsAgentEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自动分配: 在线且未达并发上限的坐席里, 选当前接待数最少的; 并列时取最近活跃的.
 *
 * @author customer-service-manager
 * @since 2.11
 */
public final class CsAgentPicker {

    private CsAgentPicker() {
    }

    public static Optional<CsAgentEntity> pick(List<CsAgentEntity> onlineAgents,
                                               Map<String, Integer> activeCounts,
                                               int defaultMaxConcurrent) {
        return onlineAgents
            .stream()
            .filter(CsAgentEntity::isOnline)
            .filter(agent -> load(agent, activeCounts) < agent.maxConcurrentOrDefault(defaultMaxConcurrent))
            .min(Comparator
                     .comparingInt((CsAgentEntity agent) -> load(agent, activeCounts))
                     .thenComparing(agent -> agent.getLastActiveAt() == null ? 0L : agent.getLastActiveAt(),
                                    Comparator.reverseOrder()));
    }

    private static int load(CsAgentEntity agent, Map<String, Integer> activeCounts) {
        return activeCounts.getOrDefault(agent.getId(), 0);
    }
}
