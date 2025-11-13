import React, { useState, useEffect } from 'react';
import { authApi } from '../services/api';
import './Admin.css';

const Admin = () => {
  const [users, setUsers] = useState([]);
  const [inviteEmail, setInviteEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [actionLoading, setActionLoading] = useState(null);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const response = await authApi.getUsers();
      setUsers(response.data);
      setError('');
    } catch (err) {
      console.error('Error fetching users:', err);
      setError('Failed to load users');
    } finally {
      setLoading(false);
    }
  };

  const handleInvite = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      setActionLoading('invite');
      await authApi.invite(inviteEmail);
      setSuccess(`Invitation sent successfully to ${inviteEmail}`);
      setInviteEmail('');
      setTimeout(() => setSuccess(''), 5000);
    } catch (err) {
      console.error('Error sending invitation:', err);
      setError(err.response?.data?.message || 'Failed to send invitation');
    } finally {
      setActionLoading(null);
    }
  };

  const handleToggleUser = async (userId, currentStatus) => {
    try {
      setActionLoading(`toggle-${userId}`);
      await authApi.toggleUser(userId);
      setSuccess(`User ${currentStatus ? 'disabled' : 'enabled'} successfully`);
      setTimeout(() => setSuccess(''), 3000);
      await fetchUsers();
    } catch (err) {
      console.error('Error toggling user:', err);
      setError(err.response?.data?.message || 'Failed to update user status');
    } finally {
      setActionLoading(null);
    }
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  if (loading) {
    return (
      <div className="container">
        <div className="loading">Loading admin dashboard...</div>
      </div>
    );
  }

  return (
    <div className="container">
      <div className="admin-container">
        <div className="admin-header">
          <h1>Admin Dashboard</h1>
          <p>Manage users and send invitations</p>
        </div>

        {error && (
          <div className="error-message">
            {error}
          </div>
        )}

        {success && (
          <div className="success">
            {success}
          </div>
        )}

        <div className="admin-section">
          <div className="card">
            <h2>Send Invitation</h2>
            <form onSubmit={handleInvite} className="invite-form">
              <div className="form-group-inline">
                <input
                  type="email"
                  className="input"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="Enter email address"
                  required
                  disabled={actionLoading === 'invite'}
                />
                <button
                  type="submit"
                  className="button button-primary"
                  disabled={actionLoading === 'invite'}
                >
                  {actionLoading === 'invite' ? 'Sending...' : 'Send Invitation'}
                </button>
              </div>
            </form>
          </div>
        </div>

        <div className="admin-section">
          <div className="card">
            <h2>User Management</h2>
            <div className="table-container">
              <table className="table">
                <thead>
                  <tr>
                    <th>Email</th>
                    <th>Status</th>
                    <th>Created Date</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.length === 0 ? (
                    <tr>
                      <td colSpan="4" style={{ textAlign: 'center' }}>
                        No users found
                      </td>
                    </tr>
                  ) : (
                    users.map((user) => (
                      <tr key={user.id}>
                        <td>{user.email}</td>
                        <td>
                          <span className={`badge ${user.enabled ? 'badge-success' : 'badge-danger'}`}>
                            {user.enabled ? 'Enabled' : 'Disabled'}
                          </span>
                        </td>
                        <td>{formatDate(user.createdAt)}</td>
                        <td>
                          <button
                            className={`button ${user.enabled ? 'button-warning' : 'button-success'} button-small`}
                            onClick={() => handleToggleUser(user.id, user.enabled)}
                            disabled={actionLoading === `toggle-${user.id}`}
                          >
                            {actionLoading === `toggle-${user.id}`
                              ? 'Loading...'
                              : user.enabled
                              ? 'Disable'
                              : 'Enable'}
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Admin;
