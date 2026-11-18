import { apiFetch } from '../../api/api.js';

export const authApi = {
  profile: () => apiFetch('/api/auth/me'),
  login: (username, password) => apiFetch('/api/auth/login', {
    method: 'POST', body: JSON.stringify({ username, password }),
  }),
  logout: () => apiFetch('/api/auth/logout', { method: 'POST' }),
  createOwner: (city, account) => apiFetch(`/api/${encodeURIComponent(city)}/owners`, {
    method: 'POST', body: JSON.stringify(account),
  }),
};
