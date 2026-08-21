package com.llm.nexusai_gateway.Decision;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LinUcbStateRepository extends JpaRepository<LinUcbState, Long> {
    Optional<LinUcbState> findByScopeIdAndProviderAndMatrixType(String scopeId, String provider, String matrixType);
}
