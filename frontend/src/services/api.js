import axios from 'axios';

// Use environment variable for API URL, fallback to localhost for development
const BASE_API_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const API_BASE_URL = BASE_API_URL.endsWith('/api') ? BASE_API_URL : `${BASE_API_URL}`;
const API_V2_BASE_URL = BASE_API_URL.replace(/\/api$/, '/api/v2');

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const apiV2 = axios.create({
  baseURL: API_V2_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add request interceptor to include Bearer token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

apiV2.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Messages API (V2 - MongoDB + ElasticSearch)
export const messagesApi = {
  getAll: () => apiV2.get('/messages'),
  getById: (id) => apiV2.get(`/messages/${id}`),
  getByType: (type) => apiV2.get(`/messages/type/${type}`),
  getByStatus: (status) => apiV2.get(`/messages/status/${status}`),
  getUnmatched: (page = 0, size = 10, sortBy = 'timestamp', sortDirection = 'desc') =>
    apiV2.get('/messages/unmatched', {
      params: { page, size, sortBy, sortDirection }
    }),
  upload: (data) => apiV2.post('/messages', data),
  uploadFile: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return apiV2.post('/messages/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  delete: (id) => apiV2.delete(`/messages/${id}`),
  getStatistics: () => apiV2.get('/messages/statistics'),
};

// Templates API
export const templatesApi = {
  getAll: () => api.get('/templates'),
  getById: (id) => api.get(`/templates/${id}`),
  getByType: (type) => api.get(`/templates/type/${type}`),
  getMessages: (id) => api.get(`/templates/${id}/messages`),
  extract: () => api.post('/templates/extract'),
  delete: (id) => api.delete(`/templates/${id}`),
  getStatistics: () => api.get('/templates/statistics'),
  testMatch: (data) => api.post('/templates/test-match', data),
  analyzeContent: (data) => api.post('/templates/analyze-content', data),
};

// Transactions API
export const transactionsApi = {
  getAll: (page = 0, size = 10, sortBy = 'processedAt', sortDirection = 'desc') =>
    api.get('/transactions', {
      params: { page, size, sortBy, sortDirection }
    }),
  getById: (id) => api.get(`/transactions/${id}`),
  getByMessageId: (messageId) => api.get(`/transactions/message/${messageId}`),
  previewMatch: (messageId, templateId) => api.post(`/transactions/preview-match`, { messageId, templateId }),
  getByTemplate: (templateId) => api.get(`/transactions/template/${templateId}`),
  getByStatus: (status) => api.get(`/transactions/status/${status}`),
  matchMessage: (messageId) => api.post(`/transactions/match/${messageId}`),
  update: (id, data) => api.put(`/transactions/${id}`, data),
  reanalyze: (id) => api.post(`/transactions/${id}/reanalyze`),
  getStatistics: () => api.get('/transactions/statistics'),
};

// Auth API
export const authApi = {
  login: (email, password) =>
    axios.post(`${API_BASE_URL}/auth/login`, { email, password }),
  register: (email, password, token) =>
    axios.post(`${API_BASE_URL}/auth/register?token=${token}`, { email, password }),
  invite: (email) => api.post('/auth/invite', { email }),
  getUsers: () => api.get('/auth/users'),
  toggleUser: (id) => api.put(`/auth/users/${id}/toggle`),
};

export default api;
