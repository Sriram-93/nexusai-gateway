package com.llm.nexusai_gateway.Repository;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {
    Optional<ProviderConfig> findBySlug(String slug);
    Optional<ProviderConfig> findBySlugAndTenantId(String slug, String tenantId);
    List<ProviderConfig> findByEnabledTrue();
    List<ProviderConfig> findByTenantId(String tenantId);
    List<ProviderConfig> findByTenantIdAndEnabledTrue(String tenantId);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndTenantId(String slug, String tenantId);
}
