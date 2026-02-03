package com.project.documentworkflow.service;

import com.project.documentworkflow.model.Decision;
import com.project.documentworkflow.model.Document;
import com.project.documentworkflow.model.OCRData;
import com.project.documentworkflow.model.Rule;
import com.project.documentworkflow.repository.DecisionRepository;
import com.project.documentworkflow.repository.DocumentRepository;
import com.project.documentworkflow.repository.OCRDataRepository;
import com.project.documentworkflow.repository.RuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DecisionEngineService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private OCRDataRepository ocrDataRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    public Decision evaluateDecision(Long documentId, Long ocrDataId) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        OCRData ocrData = ocrDataRepository.findById(ocrDataId)
                .orElseThrow(() -> new RuntimeException("OCR data not found"));

        Rule rule = ruleRepository.findAll().get(0);

        Decision decision = new Decision();

        if (ocrData.getConfidenceScore() < rule.getThresholdValue()) {
            decision.setDecisionType("REVIEW");
        } else {
            decision.setDecisionType("APPROVE");
        }

        decision.setDecisionSource("SYSTEM");
        decision.setDocument(document);

        return decisionRepository.save(decision);
    }
}
