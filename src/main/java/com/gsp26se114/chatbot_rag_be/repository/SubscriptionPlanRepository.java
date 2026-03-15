package com.gsp26se114.chatbot_rag_be.repository;

import com.gsp26se114.chatbot_rag_be.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    
    Optional<SubscriptionPlan> findByCode(String code);
    
    List<SubscriptionPlan> findByIsActiveTrueOrderByDisplayOrderAsc();
    
    boolean existsByCode(String code);
}
