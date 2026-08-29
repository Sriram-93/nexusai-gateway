package com.llm.nexusai_gateway.Governance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, String> {
    Optional<Budget> findByTargetTypeAndTargetId(String targetType, String targetId);
}
