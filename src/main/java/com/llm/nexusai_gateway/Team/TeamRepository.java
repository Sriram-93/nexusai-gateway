package com.llm.nexusai_gateway.Team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, String> {

    List<Team> findAllByOrganizationId(String organizationId);

    Optional<Team> findByIdAndOrganizationId(String id, String organizationId);

    List<Team> findAllByLeadUserId(String leadUserId);

    boolean existsByNameAndOrganizationId(String name, String organizationId);

    @Query("SELECT t FROM Team t WHERE t.organizationId = :orgId AND t.active = true")
    List<Team> findActiveByOrganizationId(@Param("orgId") String orgId);

    Optional<Team> findByTenantId(String tenantId);
}
