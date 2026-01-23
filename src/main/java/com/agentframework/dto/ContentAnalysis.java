package com.agentframework.dto;

import lombok.Data;

/**
 * Analysis result of MCP data content and user query.
 * Used by ConfigDeciderService to make decisions.
 */
@Data
public class ContentAnalysis {
    
    private int sourceCount;
    private boolean auditDomain;
    private boolean analysisDomain;
    private boolean codeDomain;
    private boolean complexQuery;
    private boolean hasStructuredData;
    private boolean hasTimeSeries;

    @Override
    public String toString() {
        return String.format(
                "ContentAnalysis{sources=%d, audit=%s, analysis=%s, code=%s, complex=%s, structured=%s, timeSeries=%s}",
                sourceCount, auditDomain, analysisDomain, codeDomain, complexQuery, hasStructuredData, hasTimeSeries
        );
    }
}
