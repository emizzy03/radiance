import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - Add auth token to requests
api.interceptors.request.use(
  (config) => {
    const user = localStorage.getItem('user');
    if (user) {
      try {
        const userData = JSON.parse(user);
        // Attach JWT token to requests
        if (userData.token) {
          config.headers.Authorization = `Bearer ${userData.token}`;
        }
      } catch (error) {
        console.error('Error parsing user data:', error);
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);


// Response interceptor - Handle common errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      // Handle 401 Unauthorized - redirect to login
      if (error.response.status === 401) {
        localStorage.removeItem('user');
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      }

      // Handle 403 Forbidden
      if (error.response.status === 403) {
        console.error('Access forbidden');
      }

      // Handle 500 Server Error
      if (error.response.status >= 500) {
        console.error('Server error occurred');
      }
    } else if (error.request) {
      // Network error
      console.error('Network error - please check your connection');
    }

    return Promise.reject(error);
  }
);

// Products API
export const productAPI = {
  getAll: () => api.get('/api/products'),
  getById: (id) => api.get(`/api/products/${id}`),
  create: (product) => api.post('/api/products', product),
  update: (id, product) => api.patch(`/api/products/${id}`, product),
  delete: (id) => api.delete(`/api/products/${id}`),
};

// Users API
export const userAPI = {
  getAll: () => api.get('/api/users'),
  getByUsername: (username) => api.get(`/api/users/username/${username}`),
  create: (user) => api.post('/api/users', user),
  update: (id, user) => api.patch(`/api/users/${id}`, user),
  delete: (id) => api.delete(`/api/users/${id}`),
  assignRole: (id, roleName) => api.post(`/api/users/${id}/roles/${roleName}`),
  removeRole: (id, roleName) => api.delete(`/api/users/${id}/roles/${roleName}`),
};

// Auth API (placeholder for future implementation)
export const authAPI = {
  login: (credentials) => api.post('/api/auth/login', credentials),
  register: (userData) => api.post('/api/auth/register', userData),
  logout: () => api.post('/api/auth/logout'),
};

// Upload API
export const uploadAPI = {
  uploadImage: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/api/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },
  deleteImage: (filename) => api.delete(`/api/upload/image/${filename}`),
};

export default api;

