package com.llm.nexusai_gateway.Repository;

import com.llm.nexusai_gateway.Model.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    List<RequestLog> findTop5ByUserIdOrderByIdDesc(String userId);

    /** Most recent N logs for the live activity stream (Global). */
    List<RequestLog> findTop50ByOrderByIdDesc();

    /** Most recent N logs for a specific tenant. */
    List<RequestLog> findTop50ByTenantIdOrderByIdDesc(String tenantId);

    /** Total request count (Global). */
    long count();

    /** Total request count for a specific tenant. */
    long countByTenantId(String tenantId);

    /** Total request count for a specific user. */
    long countByUserId(String userId);

    /** Average latency (Global). */
    @Query("SELECT AVG(r.latencyMs) FROM RequestLog r WHERE r.latencyMs IS NOT NULL")
    Double avgLatencyMs();

    /** Sum of all cost_usd values (Global). */
    @Query("SELECT SUM(r.costUsd) FROM RequestLog r WHERE r.costUsd IS NOT NULL")
    Double sumCostUsd();

    /** Sum of all cost_usd values for a specific tenant. */
    @Query("SELECT SUM(r.costUsd) FROM RequestLog r WHERE r.tenantId = :tenantId AND r.costUsd IS NOT NULL")
    Double sumCostUsdByTenant(String tenantId);

    /** Most recent logs for a specific user in a specific tenant. */
    List<RequestLog> findTop100ByTenantIdAndUserIdOrderByIdDesc(String tenantId, String userId);

    /** Distinct userIds who have made requests in a tenant. */
    @Query("SELECT DISTINCT r.userId FROM RequestLog r WHERE r.tenantId = :tenantId")
    List<String> findDistinctUserIdsByTenantId(String tenantId);

    /** Total requests for a specific user in a tenant. */
    long countByTenantIdAndUserId(String tenantId, String userId);

    /** Sum cost for a specific user in a tenant. */
    @Query("SELECT SUM(r.costUsd) FROM RequestLog r WHERE r.tenantId = :tenantId AND r.userId = :userId AND r.costUsd IS NOT NULL")
    Double sumCostUsdByTenantAndUser(String tenantId, String userId);

    /** Average latency for a specific user in a tenant. */
    @Query("SELECT AVG(r.latencyMs) FROM RequestLog r WHERE r.tenantId = :tenantId AND r.userId = :userId AND r.latencyMs IS NOT NULL")
    Double avgLatencyMsByTenantAndUser(String tenantId, String userId);

    /** Average latency for a specific tenant. */
    @Query("SELECT AVG(r.latencyMs) FROM RequestLog r WHERE r.tenantId = :tenantId AND r.latencyMs IS NOT NULL")
    Double avgLatencyMsByTenant(String tenantId);
}
