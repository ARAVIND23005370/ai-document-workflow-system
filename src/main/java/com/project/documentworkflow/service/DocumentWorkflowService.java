package com.project.documentworkflow.service;

import com.project.documentworkflow.dto.UploadResponse;
import com.project.documentworkflow.model.Decision;
import com.project.documentworkflow.model.Document;
import com.project.documentworkflow.model.OCRData;
import com.project.documentworkflow.repository.DocumentRepository;
import com.project.documentworkflow.repository.OCRDataRepository;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentWorkflowService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private OCRDataRepository ocrDataRepository;

    @Autowired
    private DecisionEngineService decisionEngineService;

    @Autowired
    private AuditService auditService;

    private static final Logger log = LoggerFactory.getLogger(DocumentWorkflowService.class);

    private final String uploadDir = "C:/Users/admin/documentworkflow_uploads/";

    public UploadResponse processUpload(MultipartFile file, String uploaderEmail) throws Exception {

        // Create upload folder if not exists
        File folder = new File(uploadDir);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        // Save file to disk
        String filePath = uploadDir + file.getOriginalFilename();
        Path path = Paths.get(filePath);
        Files.write(path, file.getBytes());

        log.info("Starting file upload process for file: {}", file.getOriginalFilename());

        // Read file content for confidence calculation
        String fileContent = "";
        try {
            String fileName2 = file.getOriginalFilename() != null ?
                    file.getOriginalFilename().toLowerCase() : "";

            if (fileName2.endsWith(".pdf")) {
                // Read text from PDF using PDFBox
                PDDocument pdDoc = PDDocument.load(file.getInputStream());
                PDFTextStripper stripper = new PDFTextStripper();
                fileContent = stripper.getText(pdDoc).toLowerCase();
                pdDoc.close();
            } else if (fileName2.endsWith(".docx")) {
                // Read plain text from DOCX
                fileContent = new String(file.getBytes(), "UTF-8").toLowerCase();
            } else {
                // Plain text file
                fileContent = new String(file.getBytes()).toLowerCase();
            }
        } catch (Exception e) {
            log.warn("Could not read file content: {}", e.getMessage());
            fileContent = "";
        }

        String fileName = file.getOriginalFilename() != null ?
                file.getOriginalFilename().toLowerCase() : "";

        // Calculate confidence score based on document completeness
        double confidenceScore = calculateConfidence(fileContent, fileName);

        // Extract text preview (first 500 chars)
        String extractedText = fileContent.length() > 500 ?
                fileContent.substring(0, 500) : fileContent;

        if (extractedText.trim().isEmpty()) {
            extractedText = "No readable text extracted from: " + file.getOriginalFilename();
        }

        log.info("Confidence score calculated: {} for file: {}", confidenceScore, fileName);

        // Save document record
        Document document = new Document();
        document.setDocumentType(detectDocumentType(fileContent, fileName));
        document.setFilePath(filePath);
        document.setStatus("UPLOADED");
        document.setUploadedByEmail(uploaderEmail);

        Document savedDocument = documentRepository.save(document);

        // Audit the upload
        auditService.log(savedDocument.getDocumentId(), "UPLOADED", uploaderEmail,
                "File uploaded: " + file.getOriginalFilename() +
                        " | Confidence: " + String.format("%.0f", confidenceScore * 100) + "%");

        // Save OCR data with calculated confidence
        OCRData ocrData = new OCRData();
        ocrData.setDocument(savedDocument);
        ocrData.setExtractedText(extractedText);
        ocrData.setConfidenceScore(confidenceScore);

        OCRData savedOcrData = ocrDataRepository.save(ocrData);

        // Run decision engine
        Decision decision = decisionEngineService.evaluateDecision(
                savedDocument.getDocumentId(),
                savedOcrData.getOcrDataId()
        );

        log.info("Decision {} applied for document ID {} with confidence {}",
                decision.getDecisionType(), savedDocument.getDocumentId(), confidenceScore);

        // Build response
        UploadResponse response = new UploadResponse();
        response.setDocumentId(savedDocument.getDocumentId());
        response.setDecision(decision.getDecisionType());
        response.setStatus(savedDocument.getStatus());

        return response;
    }

    // ── Confidence Score Calculation ──────────────────────────────────────────
    private double calculateConfidence(String content, String fileName) {

        // Empty or unreadable file → very low confidence
        if (content == null || content.trim().length() < 20) {
            return 0.10 + (Math.random() * 0.05);
        }

        int score = 0;
        int total = 10;

        // 1. File has reasonable length
        if (content.length() > 200) score++;

        // 2. Has applicant / person name field
        if (content.contains("name") || content.contains("applicant") ||
                content.contains("customer") || content.contains("complainant")) score++;

        // 3. Has a date in format dd/mm/yyyy or dd-mm-yyyy
        if (content.matches(".*\\d{2}[/\\-]\\d{2}[/\\-]\\d{4}.*")) score++;

        // 4. Has numbers or amounts
        if (content.matches(".*\\d+.*")) score++;

        // 5. Has address or location
        if (content.contains("address") || content.contains("city") ||
                content.contains("chennai") || content.contains("mumbai") ||
                content.contains("delhi") || content.contains("bangalore") ||
                content.contains("street") || content.contains("road") ||
                content.contains("nagar") || content.contains("district")) score++;

        // 6. Has contact information
        if (content.contains("phone") || content.contains("mobile") ||
                content.contains("email") || content.contains("@") ||
                content.contains("contact")) score++;

        // 7. Has ID or reference number
        if (content.contains("pan") || content.contains("aadhaar") ||
                content.contains("passport") || content.contains("id") ||
                content.contains("account") || content.contains("reference") ||
                content.contains("number")) score++;

        // 8. Has signature or declaration
        if (content.contains("signature") || content.contains("declare") ||
                content.contains("sign") || content.contains("authorised") ||
                content.contains("authorized")) score++;

        // 9. Has substantial non-whitespace content
        long nonSpaceChars = content.chars()
                .filter(c -> c != ' ' && c != '\n' && c != '\r' && c != '\t')
                .count();
        if (nonSpaceChars > 150) score++;

        // 10. Has document-type keywords
        if (content.contains("loan") || content.contains("complaint") ||
                content.contains("application") || content.contains("invoice") ||
                content.contains("report") || content.contains("contract") ||
                content.contains("form") || content.contains("request") ||
                content.contains("certificate") || content.contains("statement")) score++;

        // Calculate raw confidence
        double raw = (double) score / total;

        // Add small random variation (±3%) so scores aren't always identical
        double variation = (Math.random() * 0.06) - 0.03;

        double finalScore = Math.min(1.0, Math.max(0.05, raw + variation));

        log.info("Confidence breakdown — score: {}/{} → raw: {} → final: {}",
                score, total, raw, String.format("%.2f", finalScore));

        return finalScore;
    }

    // ── Document Type Detection ───────────────────────────────────────────────
    private String detectDocumentType(String content, String fileName) {
        if (content.contains("invoice") || content.contains("invoice no") ||
                fileName.contains("invoice")) return "INVOICE";

        if (content.contains("loan") || content.contains("loan application") ||
                fileName.contains("loan")) return "LOAN";

        if (content.contains("complaint") || content.contains("grievance") ||
                fileName.contains("complaint")) return "COMPLAINT";

        if (content.contains("contract") || content.contains("agreement") ||
                fileName.contains("contract")) return "CONTRACT";

        if (content.contains("report") || fileName.contains("report")) return "REPORT";

        if (content.contains("application") || fileName.contains("application")) return "APPLICATION";

        if (content.contains("identity") || content.contains("aadhaar") ||
                content.contains("passport") || fileName.contains("id")) return "IDENTITY";

        if (content.contains("mortgage") || fileName.contains("mortgage")) return "MORTGAGE";

        if (content.contains("financial") || content.contains("balance sheet") ||
                fileName.contains("financial")) return "FINANCIAL";

        return "GENERAL";
    }
}