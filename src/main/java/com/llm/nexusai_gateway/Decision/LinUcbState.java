package com.llm.nexusai_gateway.Decision;

import jakarta.persistence.*;

@Entity
@Table(name = "linucb_matrix_state")
public class LinUcbState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "GLOBAL" or actual tenantId
    @Column(nullable = false)
    private String scopeId;

    @Column(nullable = false)
    private String provider;

    // "A" or "B"
    @Column(nullable = false)
    private String matrixType;

    // Serialized 2D array using JSON
    @Column(columnDefinition = "TEXT", nullable = false)
    private String matrixData;

    public LinUcbState() {}

    public LinUcbState(String scopeId, String provider, String matrixType, String matrixData) {
        this.scopeId = scopeId;
        this.provider = provider;
        this.matrixType = matrixType;
        this.matrixData = matrixData;
    }

    public Long getId() { return id; }
    public String getScopeId() { return scopeId; }
    public String getProvider() { return provider; }
    public String getMatrixType() { return matrixType; }
    public String getMatrixData() { return matrixData; }
    
    public void setMatrixData(String matrixData) { this.matrixData = matrixData; }
}
