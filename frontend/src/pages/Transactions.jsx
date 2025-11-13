import React, { useState, useEffect } from 'react';
import { transactionsApi, messagesApi, templatesApi } from '../services/api';
import FieldFindingsDisplay from '../components/FieldFindingsDisplay';
import './Transactions.css';

function Transactions() {
  const [transactions, setTransactions] = useState([]);
  const [messages, setMessages] = useState([]);
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [selectedMessage, setSelectedMessage] = useState(null);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  const [selectedUnmatchedMessage, setSelectedUnmatchedMessage] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [matchingMessage, setMatchingMessage] = useState(null);
  const [highlightedField, setHighlightedField] = useState(null);
  const [reanalyzing, setReanalyzing] = useState(false);

  // Filter state
  const [confidenceThreshold, setConfidenceThreshold] = useState(0);
  const [showOnlyDifferences, setShowOnlyDifferences] = useState(false);
  const [showMatchedFields, setShowMatchedFields] = useState(true);
  const [showLowConfidenceFields, setShowLowConfidenceFields] = useState(true);

  // Pagination and sorting state for transactions
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [sortBy, setSortBy] = useState('processedAt');
  const [sortDirection, setSortDirection] = useState('desc');

  // Pagination and sorting state for unmatched messages
  const [unmatchedPage, setUnmatchedPage] = useState(0);
  const [unmatchedPageSize, setUnmatchedPageSize] = useState(5);
  const [unmatchedTotalPages, setUnmatchedTotalPages] = useState(0);
  const [unmatchedTotalItems, setUnmatchedTotalItems] = useState(0);
  const [unmatchedSortBy, setUnmatchedSortBy] = useState('timestamp');
  const [unmatchedSortDirection, setUnmatchedSortDirection] = useState('desc');

  useEffect(() => {
    loadData();
  }, [currentPage, pageSize, sortBy, sortDirection, unmatchedPage, unmatchedPageSize, unmatchedSortBy, unmatchedSortDirection]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [transactionsRes, unmatchedMessagesRes] = await Promise.all([
        transactionsApi.getAll(currentPage, pageSize, sortBy, sortDirection),
        messagesApi.getUnmatched(unmatchedPage, unmatchedPageSize, unmatchedSortBy, unmatchedSortDirection)
      ]);

      // Extract paginated transaction data
      setTransactions(transactionsRes.data.transactions);
      setTotalPages(transactionsRes.data.totalPages);
      setTotalItems(transactionsRes.data.totalItems);
      setCurrentPage(transactionsRes.data.currentPage);

      // Extract paginated unmatched messages data
      setMessages(unmatchedMessagesRes.data.messages);
      setUnmatchedTotalPages(unmatchedMessagesRes.data.totalPages);
      setUnmatchedTotalItems(unmatchedMessagesRes.data.totalItems);
      setUnmatchedPage(unmatchedMessagesRes.data.currentPage);

      setError(null);
    } catch (err) {
      setError('Failed to load data');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleMatchMessage = async (messageId) => {
    try {
      setMatchingMessage(messageId);
      setError(null);
      const response = await transactionsApi.matchMessage(messageId);
      setSuccess(`Message matched to template with ${(response.data.matchConfidence * 100).toFixed(1)}% confidence`);
      await loadData();
    } catch (err) {
      setError('Failed to match message');
      console.error(err);
    } finally {
      setMatchingMessage(null);
    }
  };

  const handleViewTransaction = async (transaction) => {
    // Reset previous state first
    setSelectedMessage(null);
    setSelectedTemplate(null);
    setHighlightedField(null);

    // Set new transaction
    setSelectedTransaction(transaction);

    // Load message and template details for comparison
    try {
      const [messageRes, templateRes] = await Promise.all([
        messagesApi.getById(transaction.swiftMessageId),
        templatesApi.getById(transaction.templateId)
      ]);
      setSelectedMessage(messageRes.data);
      setSelectedTemplate(templateRes.data);
    } catch (err) {
      console.error('Failed to load message/template details:', err);
    }
  };

  const handleViewUnmatchedMessage = async (message) => {
    setSelectedUnmatchedMessage(message);
  };

  const handleCloseModal = () => {
    setSelectedTransaction(null);
    setSelectedMessage(null);
    setSelectedTemplate(null);
    setHighlightedField(null);
  };

  const handleCloseUnmatchedModal = () => {
    setSelectedUnmatchedMessage(null);
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
        // For templates, match both :20: format AND {20} or ${20} format
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

  // Get field similarity color
  const getFieldSimilarityColor = (similarity) => {
    if (similarity >= 0.9) return '#4caf50'; // Green
    if (similarity >= 0.75) return '#ff9800'; // Orange
    if (similarity >= 0.5) return '#ff5722'; // Red
    return '#757575'; // Gray
  };

  // Render SWIFT content with field highlighting and similarity scores
  const renderSwiftContent = (content, isTemplate = false, showSimilarity = false, fieldConfidences = null) => {
    const fields = parseSwiftFields(content, isTemplate);

    return (
      <div className="swift-content">
        {fields.map((field, idx) => {
          const isHighlighted = highlightedField && field.tag && field.tag === highlightedField;
          const fieldConfidence = showSimilarity && fieldConfidences && field.tag ? fieldConfidences[field.tag] : null;

          // Apply filters
          if (field.tag && fieldConfidence !== null) {
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
                  console.log(`Hovering over field: ${field.tag} (isTemplate: ${isTemplate})`);
                  setHighlightedField(field.tag);
                }
              }}
              onClick={() => {
                if (field.tag) {
                  console.log(`Clicked field: ${field.tag} (isTemplate: ${isTemplate}), current highlight: ${highlightedField}`);
                  // Toggle highlighting on click
                  setHighlightedField(highlightedField === field.tag ? null : field.tag);

                  // Scroll to corresponding fields in both views
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
                {fieldConfidence && (
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

  const handleUpdateTransaction = async (transactionId, userData) => {
    try {
      await transactionsApi.update(transactionId, userData);
      setSuccess('Transaction updated successfully');
      await loadData();
      setSelectedTransaction(null);
    } catch (err) {
      setError('Failed to update transaction');
      console.error(err);
    }
  };

  const handleReanalyzeTransaction = async (transactionId) => {
    try {
      setReanalyzing(true);
      setError(null);
      const response = await transactionsApi.reanalyze(transactionId);
      setSuccess(`Transaction re-analyzed with ${(response.data.matchConfidence * 100).toFixed(1)}% confidence`);

      // Refresh transaction details
      await handleViewTransaction(selectedTransaction);
      await loadData();
    } catch (err) {
      setError('Failed to re-analyze transaction');
      console.error(err);
    } finally {
      setReanalyzing(false);
    }
  };

  // Pagination handlers for transactions
  const handlePageChange = (newPage) => {
    setCurrentPage(newPage);
  };

  const handlePageSizeChange = (newSize) => {
    setPageSize(newSize);
    setCurrentPage(0); // Reset to first page when changing page size
  };

  // Pagination handlers for unmatched messages
  const handleUnmatchedPageChange = (newPage) => {
    setUnmatchedPage(newPage);
  };

  const handleUnmatchedPageSizeChange = (newSize) => {
    setUnmatchedPageSize(newSize);
    setUnmatchedPage(0);
  };

  // Sorting handler for transactions
  const handleSort = (field) => {
    if (sortBy === field) {
      // Toggle direction if clicking same field
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      // New field, default to descending
      setSortBy(field);
      setSortDirection('desc');
    }
  };

  // Sorting handler for unmatched messages
  const handleUnmatchedSort = (field) => {
    if (unmatchedSortBy === field) {
      setUnmatchedSortDirection(unmatchedSortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setUnmatchedSortBy(field);
      setUnmatchedSortDirection('desc');
    }
  };

  // Get sort icon for table header
  const getSortIcon = (field) => {
    if (sortBy !== field) return '⇅';
    return sortDirection === 'asc' ? '↑' : '↓';
  };

  // Get sort icon for unmatched messages table header
  const getUnmatchedSortIcon = (field) => {
    if (unmatchedSortBy !== field) return '⇅';
    return unmatchedSortDirection === 'asc' ? '↑' : '↓';
  };

  if (loading) {
    return <div className="container"><div className="loading">Loading transactions...</div></div>;
  }

  return (
    <div className="container">
      <h2 className="page-title">Transactions</h2>

      {error && <div className="error">{error}</div>}
      {success && <div className="success">{success}</div>}

      {/* Unmatched Messages Section */}
      {unmatchedTotalItems > 0 && (
        <div className="card" style={{ marginBottom: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <div>
              <h3 style={{ margin: 0 }}>Unmatched Messages</h3>
              <p className="help-text" style={{ margin: '0.5rem 0 0 0' }}>These messages are ready to be matched to templates</p>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span>Items per page:</span>
              <select
                value={unmatchedPageSize}
                onChange={(e) => handleUnmatchedPageSizeChange(parseInt(e.target.value))}
                style={{ padding: '5px 10px', borderRadius: '4px', border: '1px solid #ddd' }}
              >
                <option value={5}>5</option>
                <option value={10}>10</option>
                <option value={25}>25</option>
              </select>
            </div>
          </div>

          <table className="table">
            <thead>
              <tr>
                <th
                  onClick={() => handleUnmatchedSort('messageType')}
                  style={{ cursor: 'pointer', userSelect: 'none' }}
                  title="Click to sort"
                >
                  Message Type {getUnmatchedSortIcon('messageType')}
                </th>
                <th>Sender</th>
                <th>Receiver</th>
                <th
                  onClick={() => handleUnmatchedSort('status')}
                  style={{ cursor: 'pointer', userSelect: 'none' }}
                  title="Click to sort"
                >
                  Status {getUnmatchedSortIcon('status')}
                </th>
                <th
                  onClick={() => handleUnmatchedSort('timestamp')}
                  style={{ cursor: 'pointer', userSelect: 'none' }}
                  title="Click to sort"
                >
                  Timestamp {getUnmatchedSortIcon('timestamp')}
                </th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {messages.map((message) => (
                <tr
                  key={message.id}
                  onClick={() => handleViewUnmatchedMessage(message)}
                  style={{ cursor: 'pointer' }}
                  className="clickable-row"
                >
                  <td><span className="badge badge-info">{message.messageType}</span></td>
                  <td>{message.senderId}</td>
                  <td>{message.receiverId}</td>
                  <td><span className={`badge badge-${getStatusColor(message.status)}`}>{message.status}</span></td>
                  <td>{new Date(message.timestamp).toLocaleString()}</td>
                  <td>
                    <button
                      className="button button-sm button-success"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleMatchMessage(message.id);
                      }}
                      disabled={matchingMessage === message.id}
                    >
                      {matchingMessage === message.id ? 'Matching...' : 'Match to Template'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Pagination Controls */}
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '1rem',
            padding: '1rem',
            borderTop: '1px solid #eee'
          }}>
            <div>
              Showing {unmatchedPage * unmatchedPageSize + 1} to {Math.min((unmatchedPage + 1) * unmatchedPageSize, unmatchedTotalItems)} of {unmatchedTotalItems} unmatched messages
            </div>
            <div style={{ display: 'flex', gap: '5px' }}>
              <button
                className="button button-sm"
                onClick={() => handleUnmatchedPageChange(0)}
                disabled={unmatchedPage === 0}
              >
                First
              </button>
              <button
                className="button button-sm"
                onClick={() => handleUnmatchedPageChange(unmatchedPage - 1)}
                disabled={unmatchedPage === 0}
              >
                Previous
              </button>
              <span style={{ padding: '5px 15px', display: 'flex', alignItems: 'center' }}>
                Page {unmatchedPage + 1} of {unmatchedTotalPages}
              </span>
              <button
                className="button button-sm"
                onClick={() => handleUnmatchedPageChange(unmatchedPage + 1)}
                disabled={unmatchedPage >= unmatchedTotalPages - 1}
              >
                Next
              </button>
              <button
                className="button button-sm"
                onClick={() => handleUnmatchedPageChange(unmatchedTotalPages - 1)}
                disabled={unmatchedPage >= unmatchedTotalPages - 1}
              >
                Last
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Transactions Table */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0 }}>All Transactions</h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span>Items per page:</span>
            <select
              value={pageSize}
              onChange={(e) => handlePageSizeChange(parseInt(e.target.value))}
              style={{ padding: '5px 10px', borderRadius: '4px', border: '1px solid #ddd' }}
            >
              <option value={5}>5</option>
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
            </select>
          </div>
        </div>

        {transactions.length === 0 ? (
          <div className="empty-state">
            <p>No transactions yet. Match messages to templates to create transactions.</p>
          </div>
        ) : (
          <>
            <table className="table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th
                    onClick={() => handleSort('messageType')}
                    style={{ cursor: 'pointer', userSelect: 'none' }}
                    title="Click to sort"
                  >
                    Message Type {getSortIcon('messageType')}
                  </th>
                  <th>Template ID</th>
                  <th
                    onClick={() => handleSort('matchConfidence')}
                    style={{ cursor: 'pointer', userSelect: 'none' }}
                    title="Click to sort"
                  >
                    Confidence {getSortIcon('matchConfidence')}
                  </th>
                  <th
                    onClick={() => handleSort('status')}
                    style={{ cursor: 'pointer', userSelect: 'none' }}
                    title="Click to sort"
                  >
                    Status {getSortIcon('status')}
                  </th>
                  <th
                    onClick={() => handleSort('processedAt')}
                    style={{ cursor: 'pointer', userSelect: 'none' }}
                    title="Click to sort"
                  >
                    Processed {getSortIcon('processedAt')}
                  </th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((transaction) => (
                  <tr
                    key={transaction.id}
                    onClick={() => handleViewTransaction(transaction)}
                    style={{ cursor: 'pointer' }}
                    className="clickable-row"
                  >
                    <td><code>{transaction.id.substring(0, 8)}</code></td>
                    <td><span className="badge badge-info">{transaction.messageType}</span></td>
                    <td><code>{transaction.templateId?.substring(0, 8)}</code></td>
                    <td>
                      <div className="confidence-bar-small">
                        <div
                          className="confidence-fill"
                          style={{ width: `${(transaction.matchConfidence * 100)}%` }}
                        />
                      </div>
                      <span className="confidence-value">{(transaction.matchConfidence * 100).toFixed(1)}%</span>
                    </td>
                    <td><span className={`badge badge-${getStatusColor(transaction.status)}`}>{transaction.status}</span></td>
                    <td>{new Date(transaction.processedAt).toLocaleString()}</td>
                    <td>
                      <button
                        className="button button-sm button-primary"
                        onClick={(e) => {
                          e.stopPropagation(); // Prevent double-triggering
                          handleViewTransaction(transaction);
                        }}
                      >
                        View
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Pagination Controls */}
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: '1rem',
              padding: '1rem',
              borderTop: '1px solid #eee'
            }}>
              <div>
                Showing {currentPage * pageSize + 1} to {Math.min((currentPage + 1) * pageSize, totalItems)} of {totalItems} transactions
              </div>
              <div style={{ display: 'flex', gap: '5px' }}>
                <button
                  className="button button-sm"
                  onClick={() => handlePageChange(0)}
                  disabled={currentPage === 0}
                >
                  First
                </button>
                <button
                  className="button button-sm"
                  onClick={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage === 0}
                >
                  Previous
                </button>
                <span style={{ padding: '5px 15px', display: 'flex', alignItems: 'center' }}>
                  Page {currentPage + 1} of {totalPages}
                </span>
                <button
                  className="button button-sm"
                  onClick={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage >= totalPages - 1}
                >
                  Next
                </button>
                <button
                  className="button button-sm"
                  onClick={() => handlePageChange(totalPages - 1)}
                  disabled={currentPage >= totalPages - 1}
                >
                  Last
                </button>
              </div>
            </div>
          </>
        )}
      </div>

      {/* Unmatched Message Modal */}
      {selectedUnmatchedMessage && (
        <div className="modal-overlay" onClick={handleCloseUnmatchedModal}>
          <div className="modal modal-large" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>SWIFT Message Details</h3>
                <p className="modal-subtitle">
                  <span className="badge badge-info">{selectedUnmatchedMessage.messageType}</span>
                  {' '}From {selectedUnmatchedMessage.senderId} to {selectedUnmatchedMessage.receiverId}
                  {' '}<span className={`badge badge-${getStatusColor(selectedUnmatchedMessage.status)}`}>{selectedUnmatchedMessage.status}</span>
                </p>
              </div>
              <button className="modal-close" onClick={handleCloseUnmatchedModal}>&times;</button>
            </div>
            <div className="modal-body">
              {/* Message Info */}
              <div className="transaction-info-bar">
                <div className="info-item">
                  <strong>Message ID:</strong> <code>{selectedUnmatchedMessage.id.substring(0, 12)}</code>
                </div>
                <div className="info-item">
                  <strong>Timestamp:</strong> {new Date(selectedUnmatchedMessage.timestamp).toLocaleString()}
                </div>
                <div className="info-item">
                  <strong>Sender:</strong> {selectedUnmatchedMessage.senderId}
                </div>
                <div className="info-item">
                  <strong>Receiver:</strong> {selectedUnmatchedMessage.receiverId}
                </div>
              </div>

              {/* SWIFT Message Content */}
              <div className="document-panel" style={{ height: 'auto' }}>
                <div className="panel-header">
                  <h4>Raw SWIFT Message</h4>
                  <span className="panel-subtitle">
                    {selectedUnmatchedMessage.messageType}
                  </span>
                </div>
                <div className="panel-body">
                  <div className="swift-message">
                    {renderSwiftContent(selectedUnmatchedMessage.rawContent, false, false, null)}
                  </div>
                </div>
              </div>

              {/* Action Button */}
              <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
                <button
                  className="button button-secondary"
                  onClick={handleCloseUnmatchedModal}
                >
                  Close
                </button>
                <button
                  className="button button-success"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleCloseUnmatchedModal();
                    handleMatchMessage(selectedUnmatchedMessage.id);
                  }}
                  disabled={matchingMessage === selectedUnmatchedMessage.id}
                >
                  {matchingMessage === selectedUnmatchedMessage.id ? 'Matching...' : 'Match to Template'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Transaction Details Modal */}
      {selectedTransaction && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal modal-fullscreen" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>Transaction Match Comparison</h3>
                <p className="modal-subtitle">
                  <span className="badge badge-info">{selectedTransaction.messageType}</span>
                  {' '}Confidence: {(selectedTransaction.matchConfidence * 100).toFixed(1)}%
                  {' '}<span className={`badge badge-${getStatusColor(selectedTransaction.status)}`}>{selectedTransaction.status}</span>
                </p>
              </div>
              <button className="modal-close" onClick={handleCloseModal}>&times;</button>
            </div>
            <div className="modal-body">
              {/* Transaction Info Bar */}
              <div className="transaction-info-bar">
                <div className="info-item">
                  <strong>Transaction ID:</strong> <code>{selectedTransaction.id.substring(0, 12)}</code>
                </div>
                <div className="info-item">
                  <strong>Buyer:</strong> {selectedTransaction.buyerId}
                </div>
                <div className="info-item">
                  <strong>Seller:</strong> {selectedTransaction.sellerId}
                </div>
                <div className="info-item">
                  <strong>Processed:</strong> {new Date(selectedTransaction.processedAt).toLocaleString()}
                </div>
              </div>

              {/* AI Analysis Section */}
              <div className="llm-comparison-section">
                <div className="llm-comparison-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span className="sparkles-icon">✨</span>
                    <h4>AI Analysis</h4>
                  </div>
                  <button
                    className="button button-sm button-primary"
                    onClick={() => handleReanalyzeTransaction(selectedTransaction.id)}
                    disabled={reanalyzing}
                    title="Re-run AI analysis with latest templates"
                  >
                    {reanalyzing ? '🔄 Re-analyzing...' : '🔄 Re-analyze'}
                  </button>
                </div>
                <div className="llm-comparison-content">
                  {selectedTransaction.structuredAnalysis ? (
                    <FieldFindingsDisplay structuredAnalysis={selectedTransaction.structuredAnalysis} />
                  ) : selectedTransaction.llmComparison ? (
                    // Fallback to text format for backward compatibility
                    <>
                      {selectedTransaction.llmComparison.split('\n').map((line, idx) => {
                        const trimmedLine = line.trim();
                        if (!trimmedLine) return null;

                        // Check if line starts with bullet point or dash
                        const isBullet = trimmedLine.startsWith('•') || trimmedLine.startsWith('-') || trimmedLine.startsWith('*');

                        return (
                          <div key={idx} className={isBullet ? 'llm-bullet-point' : 'llm-text-line'}>
                            {isBullet ? trimmedLine.substring(1).trim() : trimmedLine}
                          </div>
                        );
                      })}
                    </>
                  ) : (
                    <p className="llm-empty-message">AI analysis not available for this transaction.</p>
                  )}
                </div>
              </div>

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
                {/* Original Message */}
                <div className="document-panel">
                  <div className="panel-header">
                    <h4>📄 Original Message</h4>
                    <span className="panel-subtitle">
                      {selectedMessage ? `${selectedMessage.messageType} from ${selectedMessage.senderId}` : 'Loading...'}
                    </span>
                  </div>
                  <div className="panel-body">
                    {selectedMessage ? (
                      <div className="swift-message">
                        {renderSwiftContent(
                          selectedMessage.rawContent,
                          false,
                          true,
                          selectedTransaction.matchingDetails?.fieldConfidences
                        )}
                      </div>
                    ) : (
                      <div className="loading-small">Loading message...</div>
                    )}
                  </div>
                </div>

                {/* Template Format */}
                <div className="document-panel">
                  <div className="panel-header">
                    <h4>📋 Template Format</h4>
                    <span className="panel-subtitle">
                      {selectedTemplate ? `Template ${selectedTemplate.id.substring(0, 8)}` : 'Loading...'}
                    </span>
                  </div>
                  <div className="panel-body">
                    {selectedTemplate ? (
                      <>
                        <div className="swift-template">
                          {renderSwiftContent(
                            selectedTemplate.templateContent,
                            true,
                            true,
                            selectedTransaction.matchingDetails?.fieldConfidences
                          )}
                        </div>
                        <div className="template-info">
                          <p><strong>Variable Fields:</strong> {selectedTemplate.variableFields?.length || 0}</p>
                          <p><strong>Message Count:</strong> {selectedTemplate.messageCount}</p>
                          <p><strong>Template Confidence:</strong> {(selectedTemplate.confidence * 100).toFixed(1)}%</p>
                          {selectedTransaction.matchingDetails?.fieldConfidences && (
                            <p style={{fontSize: '0.85rem', marginTop: '8px', color: '#666'}}>
                              <strong>Field Confidence:</strong> Green badges (≥90%) indicate high confidence matches.
                              Orange (≥75%) and red (≥50%) show lower confidence fields.
                            </p>
                          )}
                        </div>
                      </>
                    ) : (
                      <div className="loading-small">Loading template...</div>
                    )}
                  </div>
                </div>
              </div>

              {/* Extracted Fields Section */}
              <div className="extracted-fields-section">
                <h4>📊 Extracted Fields</h4>
                <div className="fields-grid">
                  {Object.entries(selectedTransaction.extractedData || {}).map(([key, value]) => (
                    <div key={key} className="field-item">
                      <div className="field-label">{key}</div>
                      <div className="field-value">{typeof value === 'object' ? JSON.stringify(value) : value}</div>
                    </div>
                  ))}
                </div>
              </div>
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
    PENDING: 'warning',
    MATCHED: 'success',
    VALIDATED: 'success',
    APPROVED: 'success',
    REJECTED: 'danger',
    COMPLETED: 'success',
    FAILED: 'danger',
    ERROR: 'danger'
  };
  return colors[status] || 'info';
}

export default Transactions;
