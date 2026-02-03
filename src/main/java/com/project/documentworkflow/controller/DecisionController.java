package com.project.documentworkflow.controller;

import com.project.documentworkflow.dto.DecisionRequest;
import com.project.documentworkflow.model.Decision;
import com.project.documentworkflow.service.DecisionEngineService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionEngineService decisionEngineService;

    public DecisionController(DecisionEngineService decisionEngineService) {
        this.decisionEngineService = decisionEngineService;
    }

    @PostMapping("/evaluate")
    public Decision evaluateDecision(@RequestBody DecisionRequest request) {
        return decisionEngineService.evaluateDecision(
                request.getDocumentId(),
                request.getOcrDataId()
        );
    }
}
