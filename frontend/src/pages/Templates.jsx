import React, { useState, useEffect } from 'react';
import { templatesApi, transactionsApi } from '../services/api';
import './Templates.css';

function Templates() {
  const [templates, setTemplates] = useState([]);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  const [templateMessages, setTemplateMessages] = useState([]);
  const [messageTransactions, setMessageTransactions] = useState({});
  const [currentMessageIndex, setCurrentMessageIndex] = useState(0);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [extracting, setExtracting] = useState(false);
  const [highlightedField, setHighlightedField] = useState(null);
  const [viewMode, setViewMode] = useState('table'); // 'table' or 'grid'
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState('all');

  // Filter state for document comparison
  const [confidenceThreshold, setConfidenceThreshold] = useState(0);
  const [showOnlyDifferences, setShowOnlyDifferences] = useState(false);
  const [showMatchedFields, setShowMatchedFields] = useState(true);
  const [showLowConfidenceFields, setShowLowConfidenceFields] = useState(true);

  useEffect(() => {
    loadTemplates();
  }, []);

  const loadTemplates = async () => {
    try {
      setLoading(true);
      const response = await templatesApi.getAll();
      setTemplates(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to load templates');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleExtractTemplates = async () => {
    try {
      setExtracting(true);
      setError(null);
      const response = await templatesApi.extract();
      setSuccess(`Successfully extracted ${response.data.clustersCreated} templates from ${response.data.totalMessages} messages`);
      await loadTemplates();
    } catch (err) {
      setError('Failed to extract templates');
      console.error(err);
    } finally {
      setExtracting(false);
    }
  };

  const handleViewTemplate = async (template) => {
    setSelectedTemplate(template);
    setLoadingMessages(true);
    setMessageTransactions({});
    try {
      const response = await templatesApi.getMessages(template.id);
      setTemplateMessages(response.data);

      // Preview match for each message to get field confidences
      const confidencesMap = {};
      for (const message of response.data) {
        try {
          const previewResponse = await transactionsApi.previewMatch(message.id, template.id);
          if (previewResponse.data) {
            confidencesMap[message.id] = previewResponse.data;
          }
        } catch (err) {
          console.log(`Could not calculate confidences for message ${message.id}:`, err.message);
        }
      }
      setMessageTransactions(confidencesMap);
    } catch (err) {
      console.error('Failed to load messages for template:', err);
      setTemplateMessages([]);
    } finally {
      setLoadingMessages(false);
    }
  };

  const handleCloseModal = () => {
    setSelectedTemplate(null);
    setTemplateMessages([]);
    setMessageTransactions({});
    setCurrentMessageIndex(0);
    setHighlightedField(null);
  };

  // Parse SWIFT message into fields
  const parseSwiftFields = (content, isTemplate = false) => {
    if (!content) return [];

    const fields = [];
    const lines = content.split('\n');
    let currentField = null;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      let fieldMatch = null;
      let extractedTag = null;

      if (isTemplate) {
        // For templates, match both :20: format AND {20} or ${20} or {VARIABLE} format
        const standardMatch = line.match(/^:(\d+[A-Z]?):/);
        const variableMatch = line.match(/^\{(\d+[A-Z]?)\}/) || line.match(/^\$\{(\d+[A-Z]?)\}/);

        if (standardMatch) {
          extractedTag = standardMatch[1];
          fieldMatch = standardMatch;
        } else if (variableMatch) {
          extractedTag = variableMatch[1];
          fieldMatch = variableMatch;
        }
      } else {
        // For messages, only match standard :20: format
        fieldMatch = line.match(/^:(\d+[A-Z]?):/);
        if (fieldMatch) {
          extractedTag = fieldMatch[1];
        }
      }

      if (extractedTag) {
        // Save previous field if exists
        if (currentField) {
          fields.push(currentField);
        }
        // Start new field
        currentField = {
          tag: extractedTag,
          content: line,
          startLine: i,
          isTemplate: isTemplate
        };
      } else if (currentField) {
        // Continue current field (multiline)
        currentField.content += '\n' + line;
      } else {
        // Header or non-field content
        fields.push({
          tag: null,
          content: line,
          startLine: i,
          isTemplate: isTemplate
        });
      }
    }

    // Add last field
    if (currentField) {
      fields.push(currentField);
    }

    return fields;
  };

  // Get field similarity color based on confidence score
  const getFieldSimilarityColor = (similarity) => {
    if (similarity >= 0.9) return '#4caf50'; // Green
    if (similarity >= 0.75) return '#ff9800'; // Orange
    if (similarity >= 0.5) return '#ff5722'; // Red
    return '#757575'; // Gray
  };

  // Render SWIFT content with field highlighting and synchronized scrolling
  const renderSwiftContent = (content, isTemplate = false, fieldConfidences = null) => {
    const fields = parseSwiftFields(content, isTemplate);

    return (
      <div className="swift-content">
        {fields.map((field, idx) => {
          const isHighlighted = highlightedField && field.tag && field.tag === highlightedField;
          const fieldConfidence = fieldConfidences && field.tag ? fieldConfidences[field.tag] : null;

          // Apply filters (only for message fields with confidence scores)
          if (field.tag && fieldConfidence !== null && !isTemplate) {
            // Filter by confidence threshold
            if (fieldConfidence < confidenceThreshold / 100) {
              return null;
            }

            // Filter by low/high confidence
            if (!showLowConfidenceFields && fieldConfidence < 0.75) {
              return null;
            }

            if (!showMatchedFields && fieldConfidence >= 0.9) {
              return null;
            }
          }

          return (
            <div
              key={idx}
              className={`swift-field ${field.tag ? 'field-tagged' : 'field-header'} ${isHighlighted ? 'field-highlighted' : ''}`}
              data-field-tag={field.tag || undefined}
              onMouseEnter={() => {
                if (field.tag) {
                  setHighlightedField(field.tag);
                }
              }}
              onClick={() => {
                if (field.tag) {
                  // Toggle highlighting on click
                  setHighlightedField(highlightedField === field.tag ? null : field.tag);

                  // Scroll to corresponding fields in both panels
                  setTimeout(() => {
                    const allFieldsWithTag = document.querySelectorAll(`[data-field-tag="${field.tag}"]`);
                    allFieldsWithTag.forEach(element => {
                      element.scrollIntoView({
                        behavior: 'smooth',
                        block: 'center',
                        inline: 'nearest'
                      });
                    });
                  }, 50);
                }
              }}
              style={{ cursor: field.tag ? 'pointer' : 'default' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
                <div style={{ flex: 1 }}>{field.content}</div>
                {fieldConfidence !== null && fieldConfidence !== undefined && (
                  <span
                    className="field-similarity-badge"
                    style={{
                      backgroundColor: getFieldSimilarityColor(fieldConfidence),
                      color: 'white',
                      padding: '2px 8px',
                      borderRadius: '12px',
                      fontSize: '11px',
                      fontWeight: 'bold',
                      whiteSpace: 'nowrap',
                      flexShrink: 0
                    }}
                  >
                    {(fieldConfidence * 100).toFixed(0)}%
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  const handleNextMessage = () => {
    if (currentMessageIndex < templateMessages.length - 1) {
      setCurrentMessageIndex(currentMessageIndex + 1);
    }
  };

  const handlePreviousMessage = () => {
    if (currentMessageIndex > 0) {
      setCurrentMessageIndex(currentMessageIndex - 1);
    }
  };

  const handleDeleteTemplate = async (id) => {
    if (!window.confirm('Are you sure you want to delete this template?')) {
      return;
    }

    try {
      await templatesApi.delete(id);
      setSuccess('Template deleted successfully');
      await loadTemplates();
    } catch (err) {
      setError('Failed to delete template');
      console.error(err);
    }
  };

  // Filter templates
  const filteredTemplates = templates.filter(template => {
    const matchesSearch = template.messageType.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         (template.description && template.description.toLowerCase().includes(searchQuery.toLowerCase()));
    const matchesFilter = filterType === 'all' || template.messageType === filterType;
    return matchesSearch && matchesFilter;
  });

  // Group templates by trading pair (buyerId + sellerId)
  const groupedTemplates = filteredTemplates.reduce((groups, template) => {
    const tradingPairKey = `${template.buyerId || 'unknown'}_${template.sellerId || 'unknown'}`;
    if (!groups[tradingPairKey]) {
      groups[tradingPairKey] = {
        buyerId: template.buyerId || 'unknown',
        sellerId: template.sellerId || 'unknown',
        templates: []
      };
    }
    groups[tradingPairKey].templates.push(template);
    return groups;
  }, {});

  // Convert to array and sort by number of templates (descending)
  const tradingPairs = Object.values(groupedTemplates).sort((a, b) =>
    b.templates.length - a.templates.length
  );

  // Calculate statistics
  const stats = {
    total: templates.length,
    messageTypes: [...new Set(templates.map(t => t.messageType))].length,
    totalMessages: templates.reduce((sum, t) => sum + (t.messageCount || 0), 0),
    avgConfidence: templates.length > 0
      ? (templates.reduce((sum, t) => sum + (t.confidence || 0), 0) / templates.length * 100).toFixed(1)
      : 0
  };

  // Get unique message types for filter
  const messageTypes = [...new Set(templates.map(t => t.messageType))];

  if (loading) {
    return <div className="container"><div className="loading">Loading templates...</div></div>;
  }

  return (
    <div className="container">
      <div className="page-header">
        <div>
          <h2 className="page-title">Message Templates</h2>
          <p className="page-subtitle">Automatically extracted patterns from SWIFT messages</p>
        </div>
        <button
          className="button button-primary"
          onClick={handleExtractTemplates}
          disabled={extracting}
        >
          {extracting ? '⏳ Extracting...' : '✨ Extract Templates'}
        </button>
      </div>

      {error && <div className="error">{error}</div>}
      {success && <div className="success">{success}</div>}

      {/* Statistics Cards */}
      {templates.length > 0 && (
        <div className="stats-grid">
          <div className="stat-card stat-card-primary">
            <div className="stat-icon">📊</div>
            <div className="stat-content">
              <div className="stat-value">{stats.total}</div>
              <div className="stat-label">Total Templates</div>
            </div>
          </div>
          <div className="stat-card stat-card-success">
            <div className="stat-icon">📁</div>
            <div className="stat-content">
              <div className="stat-value">{stats.messageTypes}</div>
              <div className="stat-label">Message Types</div>
            </div>
          </div>
          <div className="stat-card stat-card-info">
            <div className="stat-icon">📨</div>
            <div className="stat-content">
              <div className="stat-value">{stats.totalMessages}</div>
              <div className="stat-label">Total Messages</div>
            </div>
          </div>
          <div className="stat-card stat-card-warning">
            <div className="stat-icon">🎯</div>
            <div className="stat-content">
              <div className="stat-value">{stats.avgConfidence}%</div>
              <div className="stat-label">Avg Confidence</div>
            </div>
          </div>
        </div>
      )}

      {/* Search and Filter Controls */}
      {templates.length > 0 && (
        <div className="controls-bar">
          <div className="search-box">
            <input
              type="text"
              placeholder="🔍 Search templates..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="search-input"
            />
          </div>
          <div className="filter-controls">
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
              className="filter-select"
            >
              <option value="all">All Types</option>
              {messageTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
            <div className="view-toggle">
              <button
                className={`toggle-btn ${viewMode === 'grid' ? 'active' : ''}`}
                onClick={() => setViewMode('grid')}
                title="Grid View"
              >
                ▦
              </button>
              <button
                className={`toggle-btn ${viewMode === 'table' ? 'active' : ''}`}
                onClick={() => setViewMode('table')}
                title="Table View"
              >
                ☰
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        {filteredTemplates.length === 0 ? (
          <div className="empty-state">
            {templates.length === 0 ? (
              <>
                <div className="empty-icon">📋</div>
                <h3>No Templates Yet</h3>
                <p>Upload some SWIFT messages and click "Extract Templates" to get started.</p>
              </>
            ) : (
              <>
                <div className="empty-icon">🔍</div>
                <h3>No Templates Found</h3>
                <p>No templates match your search criteria.</p>
              </>
            )}
          </div>
        ) : viewMode === 'grid' ? (
          /* Grid View - Grouped by Trading Pairs */
          <div className="templates-grouped">
            {tradingPairs.map((pair) => (
              <div key={`${pair.buyerId}_${pair.sellerId}`} className="trading-pair-group">
                <div className="trading-pair-header">
                  <div className="trading-pair-title">
                    <span className="trading-pair-label">{pair.sellerId}</span>
                    <span className="trading-pair-arrow">→</span>
                    <span className="trading-pair-label">{pair.buyerId}</span>
                  </div>
                  <span className="trading-pair-count">{pair.templates.length} template{pair.templates.length !== 1 ? 's' : ''}</span>
                </div>
                <div className="templates-grid">
                  {pair.templates.map((template) => (
                    <div key={template.id} className="template-card">
                      <div className="template-card-header">
                        <span className="badge badge-info">{template.messageType}</span>
                        <div className="template-card-confidence">{(template.confidence * 100).toFixed(0)}%</div>
                      </div>
                      <div className="template-card-body">
                        <h4>{template.description || 'Template'}</h4>
                        <div className="template-card-stats">
                          <div className="stat-item">
                            <span className="stat-icon-sm">📨</span>
                            <span>{template.messageCount} messages</span>
                          </div>
                          <div className="stat-item">
                            <span className="stat-icon-sm">🔀</span>
                            <span>{template.variableFields?.length || 0} variables</span>
                          </div>
                        </div>
                        <div className="template-card-date">
                          Created {new Date(template.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                      <div className="template-card-actions">
                        <button
                          className="button button-sm button-primary"
                          onClick={() => handleViewTemplate(template)}
                        >
                          View Details
                        </button>
                        <button
                          className="button button-sm button-danger"
                          onClick={() => handleDeleteTemplate(template.id)}
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : (
          /* Table View - Grouped by Trading Pairs */
          <div className="templates-grouped">
            {tradingPairs.map((pair) => (
              <div key={`${pair.buyerId}_${pair.sellerId}`} className="trading-pair-group">
                <div className="trading-pair-header">
                  <div className="trading-pair-title">
                    <span className="trading-pair-label">{pair.sellerId}</span>
                    <span className="trading-pair-arrow">→</span>
                    <span className="trading-pair-label">{pair.buyerId}</span>
                  </div>
                  <span className="trading-pair-count">{pair.templates.length} template{pair.templates.length !== 1 ? 's' : ''}</span>
                </div>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Type</th>
                      <th>Description</th>
                      <th>Messages</th>
                      <th>Variables</th>
                      <th>Confidence</th>
                      <th>Created</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pair.templates.map((template) => (
                      <tr key={template.id}>
                        <td><span className="badge badge-info">{template.messageType}</span></td>
                        <td>{template.description || 'Template'}</td>
                        <td>{template.messageCount}</td>
                        <td>{template.variableFields?.length || 0}</td>
                        <td>
                          <div className="confidence-bar">
                            <div
                              className="confidence-fill"
                              style={{ width: `${(template.confidence * 100)}%` }}
                            />
                            <span className="confidence-text">{(template.confidence * 100).toFixed(1)}%</span>
                          </div>
                        </td>
                        <td>{new Date(template.createdAt).toLocaleDateString()}</td>
                        <td>
                          <div className="action-buttons">
                            <button
                              className="button button-sm button-primary"
                              onClick={() => handleViewTemplate(template)}
                            >
                              View
                            </button>
                            <button
                              className="button button-sm button-danger"
                              onClick={() => handleDeleteTemplate(template.id)}
                            >
                              Delete
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        )}
      </div>

      {selectedTemplate && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal modal-fullscreen" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>Template Validation - {selectedTemplate.messageType}</h3>
                <p className="modal-subtitle">
                  <span className="badge badge-info">{selectedTemplate.messageType}</span>
                  {' '}Confidence: {(selectedTemplate.confidence * 100).toFixed(1)}%
                  {' '}|{' '}Messages: {templateMessages.length}
                </p>
              </div>
              <button className="modal-close" onClick={handleCloseModal}>&times;</button>
            </div>
            <div className="modal-body">
              {/* Template Info Bar */}
              <div className="transaction-info-bar">
                <div className="info-item">
                  <strong>Template ID:</strong> <code>{selectedTemplate.id.substring(0, 12)}</code>
                </div>
                <div className="info-item">
                  <strong>Messages Used:</strong> {selectedTemplate.messageCount}
                </div>
                <div className="info-item">
                  <strong>Variable Fields:</strong> {selectedTemplate.variableFields?.length || 0}
                </div>
                <div className="info-item">
                  <strong>Created:</strong> {new Date(selectedTemplate.createdAt).toLocaleString()}
                </div>
              </div>

              {/* Message Navigator */}
              {templateMessages.length > 0 && (
                <div className="message-navigator">
                  <button
                    className="button button-sm button-secondary"
                    onClick={handlePreviousMessage}
                    disabled={currentMessageIndex === 0}
                  >
                    ← Previous
                  </button>
                  <span className="navigator-info">
                    Message {currentMessageIndex + 1} of {templateMessages.length}
                    {templateMessages[currentMessageIndex] && (
                      <span className="message-meta">
                        {' '}- {templateMessages[currentMessageIndex].senderId} → {templateMessages[currentMessageIndex].receiverId}
                      </span>
                    )}
                  </span>
                  <button
                    className="button button-sm button-secondary"
                    onClick={handleNextMessage}
                    disabled={currentMessageIndex === templateMessages.length - 1}
                  >
                    Next →
                  </button>
                </div>
              )}

              {/* Filter Controls */}
              <div className="comparison-filters">
                <div className="filter-section">
                  <div className="filter-group">
                    <label className="filter-label">
                      Confidence Threshold
                      <span className="filter-value">{confidenceThreshold}%</span>
                    </label>
                    <div className="slider-container">
                      <input
                        id="confidence-threshold"
                        type="range"
                        min="0"
                        max="100"
                        step="5"
                        value={confidenceThreshold}
                        onChange={(e) => setConfidenceThreshold(Number(e.target.value))}
                        className="slider"
                      />
                      <div className="slider-track-fill" style={{ width: `${confidenceThreshold}%` }} />
                    </div>
                  </div>

                  <div className="filter-divider"></div>

                  <div className="filter-toggles-compact">
                    <label className="switch-label">
                      <input
                        type="checkbox"
                        checked={showMatchedFields}
                        onChange={(e) => setShowMatchedFields(e.target.checked)}
                        className="switch-input"
                      />
                      <span className="switch-slider"></span>
                      <span className="switch-text">High (≥90%)</span>
                    </label>

                    <label className="switch-label">
                      <input
                        type="checkbox"
                        checked={showLowConfidenceFields}
                        onChange={(e) => setShowLowConfidenceFields(e.target.checked)}
                        className="switch-input"
                      />
                      <span className="switch-slider"></span>
                      <span className="switch-text">Low (&lt;75%)</span>
                    </label>
                  </div>

                  <button
                    className="button button-sm button-ghost"
                    onClick={() => {
                      setConfidenceThreshold(0);
                      setShowMatchedFields(true);
                      setShowLowConfidenceFields(true);
                    }}
                    title="Reset all filters"
                  >
                    Reset
                  </button>
                </div>
              </div>

              {/* Side-by-side Document Comparison */}
              <div
                className="document-comparison"
                onMouseLeave={() => setHighlightedField(null)}
              >
                {/* Template Panel */}
                <div className="document-panel">
                  <div className="panel-header">
                    <h4>📋 Template Pattern</h4>
                    <span className="panel-subtitle">
                      Extracted from {selectedTemplate.messageCount} similar messages
                    </span>
                  </div>
                  <div className="panel-body">
                    <div className="swift-template">
                      {renderSwiftContent(selectedTemplate.templateContent, true)}
                    </div>
                    <div className="template-info">
                      <p><strong>Variable Fields:</strong> {selectedTemplate.variableFields?.length || 0}</p>
                      <p><strong>Confidence:</strong> {(selectedTemplate.confidence * 100).toFixed(1)}%</p>
                    </div>
                  </div>
                </div>

                {/* Current Message Panel */}
                <div className="document-panel">
                  <div className="panel-header">
                    <h4>📄 Reference Message {currentMessageIndex + 1}</h4>
                    {templateMessages[currentMessageIndex] && (
                      <span className="panel-subtitle">
                        From {templateMessages[currentMessageIndex].senderId} at {new Date(templateMessages[currentMessageIndex].timestamp).toLocaleString()}
                      </span>
                    )}
                  </div>
                  <div className="panel-body">
                    {loadingMessages ? (
                      <div className="loading-small">Loading messages...</div>
                    ) : templateMessages[currentMessageIndex] ? (
                      <>
                        <div className="swift-message">
                          {renderSwiftContent(
                            templateMessages[currentMessageIndex].rawContent,
                            false,
                            messageTransactions[templateMessages[currentMessageIndex].id]
                          )}
                        </div>
                        <div className="template-info">
                          <p><strong>Status:</strong> <span className={`badge badge-${getStatusColor(templateMessages[currentMessageIndex].status)}`}>{templateMessages[currentMessageIndex].status}</span></p>
                          <p><strong>Message ID:</strong> <code>{templateMessages[currentMessageIndex].id.substring(0, 12)}</code></p>
                        </div>
                      </>
                    ) : (
                      <p>No messages to display</p>
                    )}
                  </div>
                </div>
              </div>

              {/* Variable Fields Reference */}
              {selectedTemplate.variableFields && selectedTemplate.variableFields.length > 0 && (
                <div className="extracted-fields-section">
                  <h4>📊 Variable Fields ({selectedTemplate.variableFields.length})</h4>
                  <div className="fields-grid">
                    {selectedTemplate.variableFields.map((field, idx) => (
                      <div key={idx} className="field-item">
                        <div className="field-label">:{field.fieldTag}: {field.fieldName}</div>
                        <div className="field-value">
                          Type: {field.type} | Required: {field.required ? '✓' : '✗'}
                        </div>
                        <div className="field-value" style={{fontSize: '0.8rem', color: '#6c757d'}}>
                          Samples: {field.sampleValues?.slice(0, 3).join(', ') || 'N/A'}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function getStatusColor(status) {
  const colors = {
    NEW: 'info',
    EMBEDDED: 'info',
    CLUSTERED: 'warning',
    TEMPLATE_MATCHED: 'success',
    PROCESSED: 'success',
  };
  return colors[status] || 'info';
}

export default Templates;
