package com.llm.nexusai_gateway.Repository;

import com.llm.nexusai_gateway.Tenant.TenantConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, String> {
    Optional<TenantConfig> findByApiKey(String apiKey);
    Optional<TenantConfig> findByOrganizationId(String organizationId);
}
