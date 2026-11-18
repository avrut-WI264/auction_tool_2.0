import { useState } from 'react';
import { apiFetch } from '../api/api';

export default function ChangePassword({ onChanged }) {
  const [open, setOpen] = useState(false);
  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async event => {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      await apiFetch('/api/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) });
      setCurrent(''); setNew(''); setOpen(false);
      await onChanged();
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  };
  return <>
    <button className="btn btn-secondary" onClick={() => setOpen(true)}>Change Password</button>
    {open && <div className="modal-overlay"><form className="glass-panel" onSubmit={submit} style={{ padding: 24, maxWidth: 420, width: '100%' }}>
      <h3>Change Password</h3>
      <p style={{ margin: '12px 0' }}>Changing your password signs out all your sessions.</p>
      <label>Current password<input className="form-input" type="password" autoComplete="current-password" required value={currentPassword} onChange={event => setCurrent(event.target.value)} /></label>
      <label>New password<input className="form-input" type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={newPassword} onChange={event => setNew(event.target.value)} /></label>
      {error && <p role="alert" style={{ color: '#ef4444' }}>{error}</p>}
      <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving...' : 'Change Password'}</button>
        <button className="btn btn-secondary" type="button" disabled={busy} onClick={() => { setOpen(false); setCurrent(''); setNew(''); }}>Cancel</button>
      </div>
    </form></div>}
  </>;
}
