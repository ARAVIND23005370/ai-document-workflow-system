package com.project.documentworkflow.repository;

import com.project.documentworkflow.model.Decision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, Long> {
}
