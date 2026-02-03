package com.project.documentworkflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long decisionId;

    private String decisionType;
    private String decisionSource;
    private LocalDateTime decisionTime = LocalDateTime.now();

    @OneToOne
    @JoinColumn(name = "document_id")
    private Document document;

    public Long getDecisionId() {
        return decisionId;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(String decisionType) {
        this.decisionType = decisionType;
    }

    public String getDecisionSource() {
        return decisionSource;
    }

    public void setDecisionSource(String decisionSource) {
        this.decisionSource = decisionSource;
    }

    public LocalDateTime getDecisionTime() {
        return decisionTime;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }
}
