package com.project.documentworkflow.service;

import com.project.documentworkflow.exception.DocumentNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DecisionEngineService {

    @Autowired private DocumentRepository documentRepository;
    @Autowired private OCRDataRepository ocrDataRepository;
    @Autowired private RuleRepository ruleRepository;
    @Autowired private DecisionRepository decisionRepository;
    @Autowired private AuditService auditService;
    @Autowired private EmailNotificationService emailNotificationService;

    @Transactional
    public Decision evaluateDecision(Long documentId, Long ocrDataId) {

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        OCRData ocrData = ocrDataRepository.findById(ocrDataId)
                .orElseThrow(() -> new RuntimeException("OCR data not found"));

        // FEATURE: Get only ACTIVE rules, sorted by PRIORITY (1 = checked first)
        List<Rule> rules = ruleRepository.findByActiveTrueOrderByPriorityAsc();

        // Auto folder + priority
        document.setFolderPath(assignFolder(document.getDocumentType()));
        document.setPriority(assignPriority(document.getDocumentType()));

        Decision decision = new Decision();
        decision.setDecisionSource("SYSTEM");
        decision.setDocument(document);

        if (rules.isEmpty()) {
            // No rules = auto approve
            decision.setDecisionType("APPROVED");
            document.setStatus("APPROVED");
            auditService.log(documentId, "APPROVED", "SYSTEM", "No rules configured — auto approved");
        } else {
            boolean failed = false;
            String failedRuleName = "";

            // Check rules in PRIORITY ORDER (priority 1 first, then 2, then 3...)
            for (Rule rule : rules) {
                if (ocrData.getConfidenceScore() < rule.getThresholdValue()) {
                    failed = true;
                    failedRuleName = "Priority " + rule.getPriority() + " — " + rule.getRuleName();
                    break;
                }
            }

            if (failed) {
                // Check if score is in REVIEW zone (between 30% and 70%)
                double score = ocrData.getConfidenceScore();
                if (score >= 0.30 && score < 0.70) {
                    decision.setDecisionType("REVIEW");
                    decision.setDecisionSource("SYSTEM_FLAGGED");
                    decision.setDecisionReason("Confidence score " +
                            String.format("%.0f", score * 100) +
                            "% is borderline — requires manual review");
                    document.setStatus("REVIEW");
                    auditService.log(documentId, "REVIEW", "SYSTEM",
                            "Flagged for manual review — confidence: " +
                                    String.format("%.0f", score * 100) + "%");
                } else {
                    decision.setDecisionType("REJECTED");
                    decision.setDecisionSource("FAILED_RULE: " + failedRuleName);
                    decision.setDecisionReason("OCR confidence " +
                            String.format("%.0f", score * 100) +
                            "% failed rule: " + failedRuleName);
                    document.setStatus("REJECTED");
                    auditService.log(documentId, "REJECTED", "SYSTEM",
                            "Rejected by rule: " + failedRuleName);
                    emailNotificationService.sendRejectionEmailIfAllowed(document, failedRuleName);
                }
            } else {
                decision.setDecisionType("APPROVED");
                document.setStatus("APPROVED");
                auditService.log(documentId, "APPROVED", "SYSTEM",
                        "Passed all " + rules.size() + " rules");
            }

        }

        decisionRepository.save(decision);
        documentRepository.save(document);
        return decision;
    }

    private String assignFolder(String documentType) {
        if (documentType == null) return "uploads/UNKNOWN/";
        switch (documentType.toUpperCase()) {
            case "INVOICE":     return "uploads/INVOICES/";
            case "COMPLAINT":   return "uploads/COMPLAINTS/";
            case "APPLICATION": return "uploads/APPLICATIONS/";
            case "REPORT":      return "uploads/REPORTS/";
            case "CONTRACT":    return "uploads/CONTRACTS/";
            case "IDENTITY":    return "uploads/IDENTITY/";
            case "LOAN":        return "uploads/LOANS/";
            case "MORTGAGE":    return "uploads/MORTGAGES/";
            case "FINANCIAL":   return "uploads/FINANCIAL/";
            default:            return "uploads/GENERAL/";
        }
    }

    private int assignPriority(String documentType) {
        if (documentType == null) return 3;
        switch (documentType.toUpperCase()) {
            case "COMPLAINT":
            case "IDENTITY":  return 1;
            case "INVOICE":
            case "CONTRACT":  return 2;
            default:          return 3;
        }
    }
}
