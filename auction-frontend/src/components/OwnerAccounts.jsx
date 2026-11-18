import { useState } from 'react';
import { authApi } from '../features/auth/authApi';

export default function OwnerAccounts({ city, teams }) {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [teamId, setTeamId] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    try {
      const account = await authApi.createOwner(city, { username, password, teamId: Number(teamId) });
      setMessage(`Created ${account.username} for ${account.teamName}. The owner can now sign in.`);
      setUsername('');
      setPassword('');
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  };
  return <div className="glass-panel" style={{ padding: 20, marginBottom: 20 }}>
    <button className="btn btn-secondary" onClick={() => setOpen(value => !value)}>Team Owner Accounts</button>
    {open && <form onSubmit={submit} style={{ marginTop: 16, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
      <label>Team<select className="form-input" required value={teamId} onChange={e => setTeamId(e.target.value)}>
        <option value="">Select a team</option>
        {teams.map(team => <option key={team.id} value={team.id}>{team.teamName}</option>)}
      </select></label>
      <label>Owner username<input className="form-input" autoComplete="off" required pattern={'[A-Za-z0-9_.\\-]{3,60}'} value={username} onChange={e => setUsername(e.target.value)} /></label>
      <label>Password<input className="form-input" type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} /></label>
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating...' : 'Create Owner Login'}</button>
    </form>}
    {message && <p role="status" style={{ marginTop: 12 }}>{message}</p>}
  </div>;
}
