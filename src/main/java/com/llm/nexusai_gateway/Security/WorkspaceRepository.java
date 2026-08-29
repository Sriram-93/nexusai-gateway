package com.llm.nexusai_gateway.Security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    List<Workspace> findByOrganizationId(String organizationId);
}
