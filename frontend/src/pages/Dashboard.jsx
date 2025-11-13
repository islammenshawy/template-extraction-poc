import React, { useState, useEffect } from 'react';
import { messagesApi, templatesApi, transactionsApi } from '../services/api';
import './Dashboard.css';

function Dashboard() {
  const [stats, setStats] = useState({
    messages: {},
    templates: {},
    transactions: {}
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadStatistics();
  }, []);

  const loadStatistics = async () => {
    try {
      setLoading(true);
      const [messagesRes, templatesRes, transactionsRes] = await Promise.all([
        messagesApi.getStatistics(),
        templatesApi.getStatistics(),
        transactionsApi.getStatistics()
      ]);

      setStats({
        messages: messagesRes.data,
        templates: templatesRes.data,
        transactions: transactionsRes.data
      });
      setError(null);
    } catch (err) {
      setError('Failed to load statistics');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="container"><div className="loading">Loading statistics...</div></div>;
  }

  if (error) {
    return <div className="container"><div className="error">{error}</div></div>;
  }

  return (
    <div className="container">
      <h2 className="page-title">Dashboard</h2>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-icon" style={{ backgroundColor: '#007bff' }}>
            📨
          </div>
          <div className="stat-info">
            <div className="stat-label">Total Messages</div>
            <div className="stat-value">{stats.messages.totalMessages || 0}</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon" style={{ backgroundColor: '#28a745' }}>
            📋
          </div>
          <div className="stat-info">
            <div className="stat-label">Templates</div>
            <div className="stat-value">{stats.templates.totalTemplates || 0}</div>
          </div>
        </div>

        <div className="stat-card">
          <div className="stat-icon" style={{ backgroundColor: '#17a2b8' }}>
            💼
          </div>
          <div className="stat-info">
            <div className="stat-label">Transactions</div>
            <div className="stat-value">{stats.transactions.totalTransactions || 0}</div>
          </div>
        </div>
      </div>

      <div className="dashboard-grid">
        <div className="card">
          <h3>Messages by Type</h3>
          <div className="chart-container">
            {stats.messages.byMessageType && Object.keys(stats.messages.byMessageType).length > 0 ? (
              <table className="table">
                <thead>
                  <tr>
                    <th>Message Type</th>
                    <th>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(stats.messages.byMessageType).map(([type, count]) => (
                    <tr key={type}>
                      <td>{type}</td>
                      <td>{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>No messages yet</p>
            )}
          </div>
        </div>

        <div className="card">
          <h3>Messages by Status</h3>
          <div className="chart-container">
            {stats.messages.byStatus && Object.keys(stats.messages.byStatus).length > 0 ? (
              <table className="table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(stats.messages.byStatus).map(([status, count]) => (
                    <tr key={status}>
                      <td><span className={`badge badge-${getStatusColor(status)}`}>{status}</span></td>
                      <td>{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>No messages yet</p>
            )}
          </div>
        </div>

        <div className="card">
          <h3>Transaction Status</h3>
          <div className="chart-container">
            {stats.transactions.byStatus && Object.keys(stats.transactions.byStatus).length > 0 ? (
              <table className="table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(stats.transactions.byStatus).map(([status, count]) => (
                    <tr key={status}>
                      <td><span className={`badge badge-${getStatusColor(status)}`}>{status}</span></td>
                      <td>{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>No transactions yet</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function getStatusColor(status) {
  const colors = {
    NEW: 'info',
    EMBEDDED: 'info',
    CLUSTERED: 'success',
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

export default Dashboard;
