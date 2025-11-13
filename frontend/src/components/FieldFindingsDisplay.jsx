import React, { useState } from 'react';
import './FieldFindingsDisplay.css';

/**
 * Component to display structured field-level findings from LLM analysis
 * Clean table-like design
 */
function FieldFindingsDisplay({ structuredAnalysis }) {
  if (!structuredAnalysis) {
    return null;
  }

  // Map severity to colors
  const getSeverityColor = (severity) => {
    const colors = {
      CRITICAL: '#d32f2f',
      WARNING: '#f57c00',
      INFO: '#1976d2',
      ACCEPTABLE: '#388e3c'
    };
    return colors[severity] || '#757575';
  };

  // Map severity to icons
  const getSeverityIcon = (severity) => {
    const icons = {
      CRITICAL: '⚠',
      WARNING: '⚡',
      INFO: 'ℹ',
      ACCEPTABLE: '✓'
    };
    return icons[severity] || '●';
  };

  // Map risk level to colors
  const getRiskColor = (riskLevel) => {
    const colors = {
      HIGH: '#d32f2f',
      MEDIUM: '#f57c00',
      LOW: '#388e3c'
    };
    return colors[riskLevel] || '#757575';
  };

  // Sort findings by severity (Critical → Warning → Info → Acceptable)
  const severityOrder = { CRITICAL: 1, WARNING: 2, INFO: 3, ACCEPTABLE: 4 };
  const sortedFindings = (structuredAnalysis.fieldFindings || []).sort((a, b) =>
    (severityOrder[a.severity] || 5) - (severityOrder[b.severity] || 5)
  );

  return (
    <div className="field-findings-container">
      {/* Summary Bar */}
      <div className="analysis-summary-bar">
        <div className="summary-left">
          {structuredAnalysis.overallRisk && (
            <span className="risk-pill" style={{ backgroundColor: getRiskColor(structuredAnalysis.overallRisk) }}>
              {structuredAnalysis.overallRisk}
            </span>
          )}
          <span className="summary-text">{structuredAnalysis.transactionSummary || 'Field Analysis'}</span>
        </div>
        {structuredAnalysis.fieldFindings && structuredAnalysis.fieldFindings.length > 0 && (
          <div className="summary-counts">
            {['CRITICAL', 'WARNING', 'INFO', 'ACCEPTABLE'].map(severity => {
              const count = sortedFindings.filter(f => f.severity === severity).length;
              return count > 0 ? (
                <span key={severity} className="count-badge" style={{ color: getSeverityColor(severity) }}>
                  {getSeverityIcon(severity)} {count}
                </span>
              ) : null;
            })}
          </div>
        )}
      </div>

      {/* Findings Table */}
      {sortedFindings.length > 0 && (
        <div className="findings-table">
          {sortedFindings.map((finding, idx) => (
            <FindingRow key={idx} finding={finding} getSeverityColor={getSeverityColor} getSeverityIcon={getSeverityIcon} />
          ))}
        </div>
      )}

      {/* Recommendation Footer */}
      {structuredAnalysis.recommendation && (
        <div className="analysis-footer">
          <strong>→</strong> {structuredAnalysis.recommendation}
        </div>
      )}
    </div>
  );
}

/**
 * Individual finding row - cleaner table-like design
 */
function FindingRow({ finding, getSeverityColor, getSeverityIcon }) {
  const [expanded, setExpanded] = useState(false);
  const color = getSeverityColor(finding.severity);
  const hasDetails = finding.businessImpact || finding.recommendation || finding.expectedValue || finding.actualValue;

  return (
    <div className="finding-row" style={{ borderLeftColor: color }}>
      {/* Main Row */}
      <div className="finding-main" onClick={() => hasDetails && setExpanded(!expanded)}>
        <div className="finding-severity">
          <span className="severity-icon" style={{ color }}>
            {getSeverityIcon(finding.severity)}
          </span>
        </div>

        <div className="finding-field">
          <span className="field-tag" style={{ backgroundColor: color }}>
            {finding.fieldTag}
          </span>
          <span className="field-name">{finding.fieldName}</span>
        </div>

        <div className="finding-desc">
          {finding.description}
        </div>

        {hasDetails && (
          <button className="expand-btn" style={{ color }}>
            {expanded ? '▼' : '▶'}
          </button>
        )}
      </div>

      {/* Expanded Details */}
      {expanded && hasDetails && (
        <div className="finding-details">
          {(finding.expectedValue || finding.actualValue) && (
            <div className="detail-item values">
              <div className="value-comparison-expanded">
                {finding.expectedValue && (
                  <div className="value-row expected">
                    <span className="value-label">Expected:</span>
                    <span className="value-content">{finding.expectedValue}</span>
                  </div>
                )}
                {finding.actualValue && (
                  <div className="value-row actual">
                    <span className="value-label">Actual:</span>
                    <span className="value-content">{finding.actualValue}</span>
                  </div>
                )}
              </div>
            </div>
          )}
          {finding.businessImpact && (
            <div className="detail-item impact">
              <span className="detail-label">Impact:</span>
              <span className="detail-value">{finding.businessImpact}</span>
            </div>
          )}
          {finding.recommendation && (
            <div className="detail-item action">
              <span className="detail-label">Action:</span>
              <span className="detail-value">{finding.recommendation}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default FieldFindingsDisplay;
