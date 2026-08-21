package com.llm.nexusai_gateway.Agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackLogRepository extends JpaRepository<FeedbackLog, Long> {
}
