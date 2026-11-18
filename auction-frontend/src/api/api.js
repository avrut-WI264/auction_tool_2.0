export const API_BASE_URL = import.meta.env?.VITE_API_URL || (import.meta.env?.PROD ? '' : 'http://localhost:8080');

let csrfRequest;
const safeMethod = method => ['GET', 'HEAD', 'OPTIONS'].includes(method);

async function csrfToken() {
  if (!csrfRequest) {
    csrfRequest = fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: 'include', cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error('Cannot establish a secure request. Please retry.');
        return response.json();
      }).catch(error => { csrfRequest = null; throw error; });
  }
  return csrfRequest;
}

async function request(path, options, multipart, retried = false) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { ...(multipart || options.body === undefined ? {} : { 'Content-Type': 'application/json' }), ...options.headers };
  if (!safeMethod(method)) {
    const csrf = await csrfToken();
    headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options, method, headers, credentials: 'include', cache: 'no-store',
  });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    // CSRF rejection happens before the write. One refresh/retry is safe;
    // business errors and ambiguous network failures are never retried blindly.
    if (response.status === 403 && payload?.code === 'CSRF_INVALID' && !retried) {
      csrfRequest = null;
      return request(path, options, multipart, true);
    }
    const error = new Error(payload?.message || `Request failed: ${response.status} ${response.statusText}`);
    error.status = response.status;
    throw error;
  }
  return payload;
}

export const apiFetch = (path, options = {}) => request(path, options, false);
export const apiUpload = (path, body) => request(path, { method: 'POST', body }, true);
