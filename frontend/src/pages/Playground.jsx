import React, { useState, useEffect, useRef } from 'react';
import { messagesApi, templatesApi } from '../services/api';
import FieldFindingsDisplay from '../components/FieldFindingsDisplay';
import './Playground.css';

function Playground() {
  const [messages, setMessages] = useState([]);
  const [selectedMessageId, setSelectedMessageId] = useState('');
  const [editedContent, setEditedContent] = useState('');
  const [messageType, setMessageType] = useState('MT700');
  const [originalMessage, setOriginalMessage] = useState(null);
  const [matchResults, setMatchResults] = useState(null);
  const [loading, setLoading] = useState(true);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState(null);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  const [templateMessages, setTemplateMessages] = useState([]);
  const [currentMessageIndex, setCurrentMessageIndex] = useState(0);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [highlightedField, setHighlightedField] = useState(null);
  const [fieldSimilarities, setFieldSimilarities] = useState(null);
  const [aiAnalysis, setAiAnalysis] = useState(null);
  const [analyzingAI, setAnalyzingAI] = useState(false);

  // Refs for scroll sync
  const templatePanelRef = useRef(null);
  const messagePanelRef = useRef(null);

  // Filter state for document comparison
  const [confidenceThreshold, setConfidenceThreshold] = useState(0);
  const [showOnlyDifferences, setShowOnlyDifferences] = useState(false);
  const [showMatchedFields, setShowMatchedFields] = useState(true);
  const [showLowConfidenceFields, setShowLowConfidenceFields] = useState(true);

  useEffect(() => {
    loadMessages();
  }, []);

  const loadMessages = async () => {
    try {
      setLoading(true);
      const response = await messagesApi.getAll();
      setMessages(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to load messages');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleMessageSelect = (messageId) => {
    setSelectedMessageId(messageId);
    const message = messages.find(m => m.id === messageId);
    if (message) {
      setOriginalMessage(message);
      setEditedContent(message.rawContent);
      setMessageType(message.messageType);
      setMatchResults(null);
    }
  };

  const handleTestMatch = async () => {
    if (!editedContent.trim()) {
      setError('Please enter message content');
      return;
    }

    try {
      setTesting(true);
      setError(null);

      const response = await templatesApi.testMatch({
        rawContent: editedContent,
        messageType: messageType
      });

      setMatchResults(response.data);
    } catch (err) {
      setError('Failed to test message match: ' + (err.response?.data?.error || err.message));
      console.error(err);
    } finally {
      setTesting(false);
    }
  };

  const hasChanges = () => {
    return originalMessage && editedContent !== originalMessage.rawContent;
  };

  const getSimilarityColor = (similarity) => {
    if (similarity >= 0.9) return '#4caf50';
    if (similarity >= 0.75) return '#ff9800';
    if (similarity >= 0.5) return '#ff5722';
    return '#757575';
  };

  const handleViewTemplate = async (templateId) => {
    try {
      setLoadingMessages(true);
      setAnalyzingAI(true);
      const templateResponse = await templatesApi.getById(templateId);

      setSelectedTemplate(templateResponse.data);

      // Find the matching result to get field similarities
      const matchingResult = matchResults?.matches?.find(m => m.templateId === templateId);
      setFieldSimilarities(matchingResult?.fieldSimilarities || null);

      // Use the edited message being tested, not template's sample messages
      setTemplateMessages([]);
      setCurrentMessageIndex(0);
      setHighlightedField(null);

      // Trigger AI analysis for this template
      try {
        const aiResponse = await templatesApi.analyzeContent({
          rawContent: editedContent,
          messageType: messageType,
          templateId: templateId
        });
        setAiAnalysis(aiResponse.data);
      } catch (aiErr) {
        console.error('Failed to perform AI analysis:', aiErr);
        setAiAnalysis(null);
      } finally {
        setAnalyzingAI(false);
      }
    } catch (err) {
      console.error('Failed to load template details:', err);
      setError('Failed to load template details');
      setAnalyzingAI(false);
    } finally {
      setLoadingMessages(false);
    }
  };

  const handleCloseModal = () => {
    setSelectedTemplate(null);
    setTemplateMessages([]);
    setCurrentMessageIndex(0);
    setHighlightedField(null);
    setAiAnalysis(null);
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

  // Handle field click with bi-directional scroll
  const handleFieldClick = (fieldTag) => {
    if (!fieldTag) return;

    // Toggle highlight
    setHighlightedField(highlightedField === fieldTag ? null : fieldTag);

    // Scroll both panels to the clicked field
    setTimeout(() => {
      const templatePanel = templatePanelRef.current;
      const messagePanel = messagePanelRef.current;

      if (templatePanel) {
        const templateField = templatePanel.querySelector(`[data-field-tag="${fieldTag}"]`);
        if (templateField) {
          templateField.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }

      if (messagePanel) {
        const messageField = messagePanel.querySelector(`[data-field-tag="${fieldTag}"]`);
        if (messageField) {
          messageField.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }
    }, 50);
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
        fieldMatch = line.match(/^:(\d+[A-Z]?):/);
        if (fieldMatch) {
          extractedTag = fieldMatch[1];
        }
      }

      if (extractedTag) {
        if (currentField) {
          fields.push(currentField);
        }
        currentField = {
          tag: extractedTag,
          content: line,
          startLine: i,
          isTemplate: isTemplate
        };
      } else if (currentField) {
        currentField.content += '\n' + line;
      } else {
        fields.push({
          tag: null,
          content: line,
          startLine: i,
          isTemplate: isTemplate
        });
      }
    }

    if (currentField) {
      fields.push(currentField);
    }

    return fields;
  };

  // Render SWIFT content with field highlighting and similarity scores
  const renderSwiftContent = (content, isTemplate = false, showSimilarity = false) => {
    const fields = parseSwiftFields(content, isTemplate);

    const getFieldSimilarityColor = (similarity) => {
      if (similarity >= 0.9) return '#4caf50'; // Green
      if (similarity >= 0.75) return '#ff9800'; // Orange
      if (similarity >= 0.5) return '#ff5722'; // Red
      return '#757575'; // Gray
    };

    return (
      <div className="swift-content">
        {fields.map((field, idx) => {
          const isHighlighted = highlightedField && field.tag && field.tag === highlightedField;
          const fieldSim = showSimilarity && fieldSimilarities && field.tag ? fieldSimilarities[field.tag] : null;

          // Apply filters (only for message fields with similarity scores)
          if (field.tag && fieldSim && !isTemplate) {
            const similarity = fieldSim.similarity;

            // Filter by confidence threshold
            if (similarity < confidenceThreshold / 100) {
              return null;
            }

            // Filter by low/high confidence
            if (!showLowConfidenceFields && similarity < 0.75) {
              return null;
            }

            if (!showMatchedFields && similarity >= 0.9) {
              return null;
            }
          }

          return (
            <div
              key={idx}
              className={`swift-field ${field.tag ? 'field-tagged' : 'field-header'} ${isHighlighted ? 'field-highlighted' : ''}`}
              data-field-tag={field.tag}
              onMouseEnter={() => {
                if (field.tag) {
                  setHighlightedField(field.tag);
                }
              }}
              onClick={() => handleFieldClick(field.tag)}
              style={{ cursor: field.tag ? 'pointer' : 'default' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
                <div style={{ flex: 1 }}>{field.content}</div>
                {fieldSim && (
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                    <span
                      className="field-similarity-badge"
                      style={{
                        backgroundColor: getFieldSimilarityColor(fieldSim.similarity),
                        color: 'white',
                        padding: '2px 8px',
                        borderRadius: '12px',
                        fontSize: '11px',
                        fontWeight: 'bold',
                        whiteSpace: 'nowrap'
                      }}
                    >
                      {(fieldSim.similarity * 100).toFixed(0)}%
                    </span>
                    {fieldSim.matchesPattern && (
                      <span
                        style={{
                          backgroundColor: '#2196f3',
                          color: 'white',
                          padding: '2px 6px',
                          borderRadius: '8px',
                          fontSize: '9px',
                          fontWeight: 'bold',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        ✓ Pattern
                      </span>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  const getStatusColor = (status) => {
    const colors = {
      NEW: 'info',
      EMBEDDED: 'info',
      CLUSTERED: 'warning',
      TEMPLATE_MATCHED: 'success',
      PROCESSED: 'success',
    };
    return colors[status] || 'info';
  };

  if (loading) {
    return <div className="container"><div className="loading">Loading...</div></div>;
  }

  return (
    <div className="container playground-container">
      <div className="page-header">
        <h2 className="page-title">Template Matching Playground</h2>
        <p className="page-subtitle">Test how message edits affect template matching with Sentence-BERT</p>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="playground-layout">
        {/* Left Panel: Message Editor */}
        <div className="editor-panel">
          <div className="card">
            <div className="card-header">
              <h3>Message Editor</h3>
              {hasChanges() && <span className="badge badge-warning">Modified</span>}
            </div>
            <div className="card-body">
              <div className="form-group">
                <label>Select Message</label>
                <select
                  className="form-control"
                  value={selectedMessageId}
                  onChange={(e) => handleMessageSelect(e.target.value)}
                >
                  <option value="">-- Select a message --</option>
                  {messages.map((msg) => (
                    <option key={msg.id} value={msg.id}>
                      {msg.messageType} - {msg.senderId} → {msg.receiverId} ({msg.id.substring(0, 8)}...)
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label>Message Type</label>
                <select
                  className="form-control"
                  value={messageType}
                  onChange={(e) => setMessageType(e.target.value)}
                >
                  <option value="MT700">MT700</option>
                  <option value="MT710">MT710</option>
                  <option value="MT720">MT720</option>
                </select>
              </div>

              <div className="form-group">
                <label>Message Content</label>
                <textarea
                  className="form-control message-editor"
                  value={editedContent}
                  onChange={(e) => setEditedContent(e.target.value)}
                  placeholder="Paste or edit SWIFT message content here..."
                  rows={20}
                  spellCheck={false}
                />
              </div>

              <div className="button-group">
                <button
                  className="button button-primary"
                  onClick={handleTestMatch}
                  disabled={testing || !editedContent.trim()}
                >
                  {testing ? (
                    <>
                      <span className="spinner"></span>
                      <span>Analyzing...</span>
                    </>
                  ) : 'Test Match'}
                </button>
                {originalMessage && (
                  <button
                    className="button button-secondary"
                    onClick={() => setEditedContent(originalMessage.rawContent)}
                    disabled={!hasChanges()}
                  >
                    Reset
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Right Panel: Match Results */}
        <div className="results-panel">
          <div className="card">
            <div className="card-header">
              <h3>Match Results</h3>
              {matchResults && (
                <span className="badge badge-info">{matchResults.totalTemplates} templates</span>
              )}
            </div>
            <div className="card-body">
              {!matchResults ? (
                <div className="empty-state">
                  <p>Edit a message and click "Test Match" to see similarity scores with all templates.</p>
                  <p className="text-muted">This uses Sentence-BERT embeddings to calculate semantic similarity.</p>
                </div>
              ) : matchResults.matches && matchResults.matches.length > 0 ? (
                <>
                  {/* Best Match Highlight */}
                  {matchResults.bestMatch && (
                    <div className="best-match-card">
                      <div className="best-match-header">
                        <h4>Best Match</h4>
                        <div className="similarity-score-large" style={{color: 'white'}}>
                          {(matchResults.bestMatch.combinedSimilarity * 100).toFixed(1)}%
                        </div>
                      </div>
                      <p className="best-match-description">{matchResults.bestMatch.description}</p>
                      <div className="similarity-breakdown">
                        <div className="similarity-item">
                          <span className="similarity-label">Field-Level (50%):</span>
                          <span className="similarity-value">{(matchResults.bestMatch.fieldLevelSimilarity * 100).toFixed(1)}%</span>
                        </div>
                        <div className="similarity-item">
                          <span className="similarity-label">Structural (30%):</span>
                          <span className="similarity-value">{(matchResults.bestMatch.structuralSimilarity * 100).toFixed(1)}%</span>
                        </div>
                        <div className="similarity-item">
                          <span className="similarity-label">Document Text (20%):</span>
                          <span className="similarity-value">{(matchResults.bestMatch.textSimilarity * 100).toFixed(1)}%</span>
                        </div>
                        <div className="similarity-item" style={{borderTop: '1px solid rgba(255,255,255,0.3)', paddingTop: '10px', marginTop: '5px'}}>
                          <span className="similarity-label" style={{fontWeight: 'bold'}}>Total Combined:</span>
                          <span className="similarity-value" style={{fontWeight: 'bold', fontSize: '24px'}}>{(matchResults.bestMatch.combinedSimilarity * 100).toFixed(1)}%</span>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* All Matches Table */}
                  <h4 className="matches-title">All Template Matches</h4>
                  <div className="matches-table-container">
                    <table className="matches-table">
                      <thead>
                        <tr>
                          <th>Rank</th>
                          <th>Template</th>
                          <th>Combined</th>
                          <th>Text</th>
                          <th>Structure</th>
                          <th>Messages</th>
                        </tr>
                      </thead>
                      <tbody>
                        {matchResults.matches.map((match, index) => (
                          <tr
                            key={match.templateId}
                            className={`${index === 0 ? 'best-match-row' : ''} clickable-row`}
                            onClick={() => handleViewTemplate(match.templateId)}
                            style={{ cursor: 'pointer' }}
                          >
                            <td>{index + 1}</td>
                            <td>
                              <div className="template-cell">
                                <span className="badge badge-info">{match.messageType}</span>
                                <span className="template-id">{match.templateId.substring(0, 8)}...</span>
                              </div>
                            </td>
                            <td>
                              <div className="similarity-bar-container">
                                <div
                                  className="similarity-bar"
                                  style={{
                                    width: `${match.combinedSimilarity * 100}%`,
                                    backgroundColor: getSimilarityColor(match.combinedSimilarity)
                                  }}
                                />
                                <span className="similarity-text">{(match.combinedSimilarity * 100).toFixed(1)}%</span>
                              </div>
                            </td>
                            <td className="text-center">{(match.textSimilarity * 100).toFixed(1)}%</td>
                            <td className="text-center">{(match.structuralSimilarity * 100).toFixed(1)}%</td>
                            <td className="text-center">{match.messageCount}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              ) : (
                <div className="empty-state">
                  <p>{matchResults.message}</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Template Details Modal */}
      {selectedTemplate && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal modal-fullscreen" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>Template Comparison - {selectedTemplate.messageType}</h3>
                <p className="modal-subtitle">
                  <span className="badge badge-info">{selectedTemplate.messageType}</span>
                  {' '}Template Confidence: {(selectedTemplate.confidence * 100).toFixed(1)}%
                  {' '}|{' '} Based on {selectedTemplate.messageCount} messages
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

              {/* Message Info Bar */}
              <div className="message-navigator">
                <span className="navigator-info">
                  Your Edited Message
                  <span className="message-meta">
                    {' '}- {messageType} Message
                  </span>
                </span>
              </div>

              {/* AI Analysis Section */}
              {analyzingAI && (
                <div className="ai-analysis-loading">
                  <div className="loading-content">
                    <div className="loading-spinner-large">
                      <div className="spinner-ring"></div>
                      <div className="spinner-ring"></div>
                      <div className="spinner-ring"></div>
                    </div>
                    <div className="loading-text">
                      <span className="loading-title">🤖 AI Analysis in Progress</span>
                      <span className="loading-subtitle">Analyzing field-level differences and potential issues...</span>
                    </div>
                  </div>
                </div>
              )}
              {aiAnalysis && (
                <div className="ai-analysis-section">
                  <h4 className="ai-analysis-header">🤖 AI Analysis</h4>
                  <FieldFindingsDisplay structuredAnalysis={aiAnalysis} />
                </div>
              )}

              {/* Filter Controls */}
              <div className="comparison-filters">
                <div className="filter-header">
                  <span className="filter-title">🔍 Field Filters</span>
                  <button
                    className="filter-reset-btn"
                    onClick={() => {
                      setConfidenceThreshold(0);
                      setShowMatchedFields(true);
                      setShowLowConfidenceFields(true);
                    }}
                    title="Reset all filters"
                  >
                    ↺ Reset
                  </button>
                </div>

                <div className="filter-body">
                  {/* Confidence Pills */}
                  <div className="filter-group">
                    <span className="filter-group-label">Show Fields:</span>
                    <div className="filter-pills">
                      <button
                        className={`filter-pill ${showMatchedFields ? 'active' : ''} pill-high`}
                        onClick={() => setShowMatchedFields(!showMatchedFields)}
                        title="Show high confidence fields (≥90%)"
                      >
                        <span className="pill-indicator">●</span>
                        High ≥90%
                      </button>
                      <button
                        className={`filter-pill ${showLowConfidenceFields ? 'active' : ''} pill-low`}
                        onClick={() => setShowLowConfidenceFields(!showLowConfidenceFields)}
                        title="Show low confidence fields (<75%)"
                      >
                        <span className="pill-indicator">●</span>
                        Low &lt;75%
                      </button>
                    </div>
                  </div>

                  {/* Threshold Slider */}
                  <div className="filter-group">
                    <span className="filter-group-label">Threshold: <strong>{confidenceThreshold}%</strong></span>
                    <input
                      id="confidence-threshold"
                      type="range"
                      min="0"
                      max="100"
                      step="5"
                      value={confidenceThreshold}
                      onChange={(e) => setConfidenceThreshold(Number(e.target.value))}
                      className="filter-slider"
                      title={`Minimum confidence: ${confidenceThreshold}%`}
                    />
                  </div>
                </div>
              </div>

              {/* Side-by-side Document Comparison */}
              <div
                className="document-comparison"
                onMouseLeave={() => setHighlightedField(null)}
              >
                {/* Template Panel */}
                <div className="document-panel" ref={templatePanelRef}>
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
                <div className="document-panel" ref={messagePanelRef}>
                  <div className="panel-header">
                    <h4>📄 Your Edited Message</h4>
                    <span className="panel-subtitle">
                      The message you are testing
                    </span>
                  </div>
                  <div className="panel-body">
                    <div className="swift-message">
                      {renderSwiftContent(editedContent, false, true)}
                    </div>
                    <div className="template-info">
                      <p><strong>Message Type:</strong> <span className="badge badge-info">{messageType}</span></p>
                      {hasChanges() && <p><strong>Status:</strong> <span className="badge badge-warning">Modified from original</span></p>}
                      {fieldSimilarities && <p style={{fontSize: '0.85rem', marginTop: '8px', color: '#666'}}>
                        <strong>Field Similarity:</strong> Green badges (≥90%) indicate perfect pattern matches. Lower scores show semantic similarity.
                      </p>}
                    </div>
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

export default Playground;
