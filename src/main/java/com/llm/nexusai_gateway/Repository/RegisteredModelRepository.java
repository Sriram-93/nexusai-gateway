package com.llm.nexusai_gateway.Repository;

import com.llm.nexusai_gateway.Provider.RegisteredModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegisteredModelRepository extends JpaRepository<RegisteredModel, Long> {

    List<RegisteredModel> findByEnabledTrue();

    List<RegisteredModel> findByProviderSlug(String providerSlug);

    List<RegisteredModel> findByProviderSlugAndEnabledTrue(String providerSlug);

    Optional<RegisteredModel> findByArmKey(String armKey);

    Optional<RegisteredModel> findByProviderSlugAndModelId(String providerSlug, String modelId);

    List<RegisteredModel> findAllByProviderSlugAndModelId(String providerSlug, String modelId);

    boolean existsByArmKey(String armKey);

    /** Returns all enabled arms sorted by inputPricePer1M ascending (cheapest first). */
    @Query("SELECT m FROM RegisteredModel m WHERE m.enabled = true ORDER BY m.inputPricePer1M ASC")
    List<RegisteredModel> findEnabledOrderByPriceAsc();

    /** Returns all enabled arms sorted by estimatedLatencyMs ascending (fastest first). */
    @Query("SELECT m FROM RegisteredModel m WHERE m.enabled = true ORDER BY m.estimatedLatencyMs ASC")
    List<RegisteredModel> findEnabledOrderByLatencyAsc();

    /** Returns all models where pricing has not yet been verified. */
    List<RegisteredModel> findByPricingVerifiedFalse();
}
