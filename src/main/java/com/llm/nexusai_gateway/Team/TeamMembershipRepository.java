package com.llm.nexusai_gateway.Team;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, String> {

    List<TeamMembership> findAllByTeamId(String teamId);

    List<TeamMembership> findAllByUserId(String userId);

    Optional<TeamMembership> findByTeamIdAndUserId(String teamId, String userId);

    boolean existsByTeamIdAndUserId(String teamId, String userId);

    void deleteByTeamIdAndUserId(String teamId, String userId);

    long countByTeamId(String teamId);
}
