import { useState, useEffect } from 'react';
import axios from 'axios';
import './Clusters.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const Clusters = () => {
  const [clusterData, setClusterData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [numClusters, setNumClusters] = useState(4);
  const [selectedCluster, setSelectedCluster] = useState(null);
  const [selectedMessage, setSelectedMessage] = useState(null);

  const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2'];

  useEffect(() => {
    fetchClusterData();
  }, [numClusters]);

  const fetchClusterData = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get(`${API_URL}/clusters/visualize`, {
        params: { numClusters }
      });
      setClusterData(response.data);
    } catch (err) {
      setError(err.message || 'Failed to load cluster data');
    } finally {
      setLoading(false);
    }
  };

  const handleRegenerateClusters = () => {
    fetchClusterData();
  };

  const handleViewMessage = async (messageId) => {
    try {
      const response = await axios.get(`${API_URL}/v2/messages/${messageId}`);
      setSelectedMessage(response.data);
    } catch (err) {
      console.error('Failed to load message:', err);
    }
  };

  const handleCloseMessageModal = () => {
    setSelectedMessage(null);
  };

  if (loading) {
    return (
      <div className="clusters-container">
        <div className="loading">
          <div className="spinner"></div>
          <p>Loading clusters...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="clusters-container">
        <div className="error-message">
          <h3>Error Loading Clusters</h3>
          <p>{error}</p>
          <button onClick={fetchClusterData} className="btn-primary">Retry</button>
        </div>
      </div>
    );
  }

  if (!clusterData || !clusterData.clusters) {
    return (
      <div className="clusters-container">
        <div className="empty-state">
          <h3>No Clusters Available</h3>
          <p>Upload messages first to generate clusters</p>
        </div>
      </div>
    );
  }

  const { clusters, statistics } = clusterData;

  return (
    <div className="clusters-container">
      <div className="clusters-header">
        <div>
          <h1>Message Clusters</h1>
          <p className="subtitle">
            {statistics.totalMessages} messages grouped into {statistics.totalClusters} clusters
          </p>
        </div>
        <div className="cluster-controls">
          <label>
            Number of Clusters:
            <select
              value={numClusters}
              onChange={(e) => setNumClusters(parseInt(e.target.value))}
              className="cluster-select"
            >
              {[2, 3, 4, 5, 6, 7, 8, 9, 10].map(n => (
                <option key={n} value={n}>{n}</option>
              ))}
            </select>
          </label>
          <button onClick={handleRegenerateClusters} className="btn-primary">
            Regenerate
          </button>
        </div>
      </div>

      {/* Statistics Overview */}
      <div className="statistics-grid">
        <div className="stat-card">
          <h3>{statistics.totalMessages}</h3>
          <p>Total Messages</p>
        </div>
        <div className="stat-card">
          <h3>{statistics.totalClusters}</h3>
          <p>Clusters</p>
        </div>
        {statistics.messageTypes && Object.entries(statistics.messageTypes).map(([type, count]) => (
          <div key={type} className="stat-card">
            <h3>{count}</h3>
            <p>{type}</p>
          </div>
        ))}
      </div>

      {/* Cluster Grid */}
      <div className="clusters-grid">
        {clusters.map((cluster, index) => (
          <div
            key={cluster.id}
            className={`cluster-card ${selectedCluster === cluster.id ? 'selected' : ''}`}
            onClick={() => setSelectedCluster(cluster.id)}
            style={{ borderLeft: `4px solid ${colors[index % colors.length]}` }}
          >
            <div className="cluster-header">
              <h3>
                <span
                  className="cluster-dot"
                  style={{ backgroundColor: colors[index % colors.length] }}
                ></span>
                Cluster {cluster.id}
              </h3>
              <span className="cluster-size">{cluster.size} messages</span>
            </div>

            <div className="cluster-stats">
              <div className="stat-row">
                <strong>Message Types:</strong>
                <div className="type-distribution">
                  {Object.entries(cluster.typeDistribution).map(([type, count]) => (
                    <span key={type} className="type-tag">
                      {type}: {count}
                    </span>
                  ))}
                </div>
              </div>

              <div className="stat-row">
                <strong>Top Senders:</strong>
                <div className="sender-list">
                  {Object.entries(cluster.senderDistribution)
                    .sort(([,a], [,b]) => b - a)
                    .slice(0, 3)
                    .map(([sender, count]) => (
                      <span key={sender} className="sender-tag">
                        {sender} ({count})
                      </span>
                    ))}
                </div>
              </div>
            </div>

            {selectedCluster === cluster.id && (
              <div className="cluster-details">
                <h4>Messages in this cluster:</h4>
                <div className="message-list">
                  {cluster.messages.map((msg) => (
                    <div
                      key={msg.id}
                      className="message-item clickable"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleViewMessage(msg.id);
                      }}
                      title="Click to view full message"
                    >
                      <span className="message-id">{msg.id.substring(0, 8)}...</span>
                      <span className="message-type">{msg.messageType}</span>
                      <span className="message-route">
                        {msg.senderId} → {msg.receiverId}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Cluster Map Visualization */}
      <div className="visualization-section">
        <h2>Cluster Map (2D Projection)</h2>
        <div className="chart-container">
          <svg width="100%" height="600" viewBox="0 0 800 600">
            {/* Background grid */}
            <defs>
              <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
                <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#e0e0e0" strokeWidth="0.5"/>
              </pattern>
            </defs>
            <rect width="800" height="600" fill="url(#grid)" />

            {/* Axes */}
            <line x1="50" y1="550" x2="750" y2="550" stroke="#999" strokeWidth="2" />
            <line x1="50" y1="550" x2="50" y2="50" stroke="#999" strokeWidth="2" />
            <text x="400" y="590" textAnchor="middle" fontSize="14" fill="#666">
              Principal Component 1
            </text>
            <text x="20" y="300" textAnchor="middle" fontSize="14" fill="#666" transform="rotate(-90 20 300)">
              Principal Component 2
            </text>

            {/* Calculate bounds for proper scaling */}
            {(() => {
              const allPoints = clusters.flatMap(c => c.messages.map(m => ({ x: m.x, y: m.y })));
              const minX = Math.min(...allPoints.map(p => p.x));
              const maxX = Math.max(...allPoints.map(p => p.x));
              const minY = Math.min(...allPoints.map(p => p.y));
              const maxY = Math.max(...allPoints.map(p => p.y));
              const rangeX = maxX - minX || 1;
              const rangeY = maxY - minY || 1;

              // Calculate cluster centroids
              const centroids = clusters.map(cluster => {
                const points = cluster.messages.map(m => ({ x: m.x, y: m.y }));
                const centroidX = points.reduce((sum, p) => sum + p.x, 0) / points.length;
                const centroidY = points.reduce((sum, p) => sum + p.y, 0) / points.length;
                return { x: centroidX, y: centroidY, cluster };
              });

              return (
                <>
                  {/* Draw cluster boundaries (convex hull approximation) */}
                  {centroids.map(({ x, y, cluster }, index) => {
                    const plotX = 50 + ((x - minX) / rangeX) * 700;
                    const plotY = 550 - ((y - minY) / rangeY) * 500;
                    const color = colors[cluster.id % colors.length];
                    const isSelected = selectedCluster === cluster.id;

                    // Draw a circle around each cluster centroid
                    return (
                      <g key={`centroid-${cluster.id}`}>
                        <circle
                          cx={plotX}
                          cy={plotY}
                          r="80"
                          fill={color}
                          opacity="0.1"
                          stroke={color}
                          strokeWidth={isSelected ? "2" : "1"}
                          strokeDasharray={isSelected ? "5,5" : "none"}
                        />
                        <circle
                          cx={plotX}
                          cy={plotY}
                          r="10"
                          fill={color}
                          opacity="0.6"
                          stroke="#fff"
                          strokeWidth="2"
                        />
                        <text
                          x={plotX}
                          y={plotY - 25}
                          textAnchor="middle"
                          fontSize="12"
                          fontWeight="bold"
                          fill={color}
                        >
                          C{cluster.id}
                        </text>
                      </g>
                    );
                  })}

                  {/* Plot points */}
                  {clusters.map((cluster) => {
                    return cluster.messages.map((msg) => {
                      const plotX = 50 + ((msg.x - minX) / rangeX) * 700;
                      const plotY = 550 - ((msg.y - minY) / rangeY) * 500;
                      const color = colors[cluster.id % colors.length];
                      const isSelected = selectedCluster === cluster.id;

                      return (
                        <g key={msg.id}>
                          <circle
                            cx={plotX}
                            cy={plotY}
                            r={isSelected ? 7 : 5}
                            fill={color}
                            opacity={selectedCluster === null || isSelected ? 0.8 : 0.3}
                            stroke={isSelected ? '#fff' : color}
                            strokeWidth={isSelected ? 2 : 1}
                            style={{ cursor: 'pointer', transition: 'all 0.3s' }}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleViewMessage(msg.id);
                            }}
                          >
                            <title>{`${msg.messageType} - ${msg.senderId} → ${msg.receiverId}\nCluster ${cluster.id}\nClick to view message`}</title>
                          </circle>
                        </g>
                      );
                    });
                  })}
                </>
              );
            })()}

            {/* Legend */}
            <g transform="translate(600, 20)">
              {clusters.map((cluster, index) => (
                <g key={cluster.id} transform={`translate(0, ${index * 25})`}>
                  <circle
                    cx="10"
                    cy="10"
                    r="6"
                    fill={colors[cluster.id % colors.length]}
                    opacity={selectedCluster === null || selectedCluster === cluster.id ? 0.8 : 0.3}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setSelectedCluster(cluster.id)}
                  />
                  <text
                    x="25"
                    y="15"
                    fontSize="12"
                    fill={selectedCluster === null || selectedCluster === cluster.id ? '#2c3e50' : '#aaa'}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setSelectedCluster(cluster.id)}
                  >
                    Cluster {cluster.id} ({cluster.size})
                  </text>
                </g>
              ))}
              {selectedCluster !== null && (
                <g transform={`translate(0, ${clusters.length * 25 + 10})`}>
                  <text
                    x="10"
                    y="10"
                    fontSize="11"
                    fill="#3498db"
                    style={{ cursor: 'pointer' }}
                    onClick={() => setSelectedCluster(null)}
                  >
                    Clear Selection
                  </text>
                </g>
              )}
            </g>
          </svg>
        </div>
      </div>

      {/* Visualization Area */}
      <div className="visualization-section">
        <h2>Cluster Distribution</h2>
        <div className="chart-container">
          <svg width="100%" height="400" viewBox="0 0 800 400">
            {/* Simple bar chart */}
            {clusters.map((cluster, index) => {
              const barWidth = 60;
              const maxSize = Math.max(...clusters.map(c => c.size));
              const barHeight = (cluster.size / maxSize) * 300;
              const x = 100 + index * 120;
              const y = 350 - barHeight;

              return (
                <g key={cluster.id}>
                  <rect
                    x={x}
                    y={y}
                    width={barWidth}
                    height={barHeight}
                    fill={colors[index % colors.length]}
                    opacity={selectedCluster === cluster.id ? 1 : 0.7}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setSelectedCluster(cluster.id)}
                  />
                  <text x={x + barWidth / 2} y={370} textAnchor="middle" fontSize="12">
                    Cluster {cluster.id}
                  </text>
                  <text x={x + barWidth / 2} y={y - 5} textAnchor="middle" fontSize="14" fontWeight="bold">
                    {cluster.size}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>
      </div>

      {/* Message Type Distribution */}
      {statistics.messageTypes && (
        <div className="visualization-section">
          <h2>Message Type Distribution</h2>
          <div className="chart-container">
            <svg width="100%" height="300" viewBox="0 0 800 300">
              {Object.entries(statistics.messageTypes).map(([type, count], index) => {
                const total = Object.values(statistics.messageTypes).reduce((a, b) => a + b, 0);
                const percentage = (count / total) * 100;
                const barWidth = (percentage / 100) * 600;
                const x = 150;
                const y = 50 + index * 60;

                return (
                  <g key={type}>
                    <text x={x - 10} y={y + 20} textAnchor="end" fontSize="14">
                      {type}
                    </text>
                    <rect
                      x={x}
                      y={y}
                      width={barWidth}
                      height={35}
                      fill={colors[index % colors.length]}
                      opacity="0.8"
                    />
                    <text x={x + barWidth + 10} y={y + 22} fontSize="14" fontWeight="bold">
                      {count} ({percentage.toFixed(1)}%)
                    </text>
                  </g>
                );
              })}
            </svg>
          </div>
        </div>
      )}

      {/* Message View Modal */}
      {selectedMessage && (
        <div className="modal-overlay" onClick={handleCloseMessageModal}>
          <div className="modal modal-large" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>SWIFT Message Details</h3>
                <p className="modal-subtitle">
                  <span className="badge badge-info">{selectedMessage.messageType}</span>
                  {' '}From {selectedMessage.senderId} to {selectedMessage.receiverId}
                  {' '}<span className={`badge badge-${selectedMessage.status === 'CLUSTERED' ? 'success' : 'info'}`}>{selectedMessage.status}</span>
                </p>
              </div>
              <button className="modal-close" onClick={handleCloseMessageModal}>&times;</button>
            </div>
            <div className="modal-body">
              {/* Message Info */}
              <div className="transaction-info-bar">
                <div className="info-item">
                  <strong>Message ID:</strong> <code>{selectedMessage.id.substring(0, 12)}</code>
                </div>
                <div className="info-item">
                  <strong>Timestamp:</strong> {new Date(selectedMessage.timestamp).toLocaleString()}
                </div>
                <div className="info-item">
                  <strong>Sender:</strong> {selectedMessage.senderId}
                </div>
                <div className="info-item">
                  <strong>Receiver:</strong> {selectedMessage.receiverId}
                </div>
              </div>

              {/* SWIFT Message Content */}
              <div className="document-panel" style={{ height: 'auto' }}>
                <div className="panel-header">
                  <h4>Raw SWIFT Message</h4>
                  <span className="panel-subtitle">
                    {selectedMessage.messageType}
                  </span>
                </div>
                <div className="panel-body">
                  <pre className="swift-message" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {selectedMessage.rawContent}
                  </pre>
                </div>
              </div>

              {/* Action Button */}
              <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
                <button
                  className="button button-secondary"
                  onClick={handleCloseMessageModal}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Clusters;
