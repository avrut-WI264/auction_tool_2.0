import { useCallback, useEffect, useRef, useState } from 'react';
import { authApi } from './authApi.js';

/** Server-verified identity is separate from route/localStorage preferences. */
export function useAuthSession() {
  const [authUser, setUser] = useState(null);
  const [authReady, setReady] = useState(false);
  const generation = useRef(0);

  useEffect(() => {
    let disposed = false;
    const version = generation.current;
    const current = () => !disposed && version === generation.current;
    authApi.profile().then(user => { if (current()) setUser(user); })
      .catch(() => { if (current()) setUser(null); })
      .finally(() => { if (current()) setReady(true); });
    return () => { disposed = true; };
  }, []);

  const authenticate = useCallback(async (username, password) => {
    const version = ++generation.current;
    try {
      const user = await authApi.login(username, password);
      if (version === generation.current) setUser(user);
      return user;
    } finally {
      if (version === generation.current) setReady(true);
    }
  }, []);

  const endSession = useCallback(async () => {
    const version = ++generation.current;
    try {
      await authApi.logout();
    } catch {
      // Local identity is cleared even when the network is unavailable.
    } finally {
      if (version === generation.current) {
        setUser(null);
        setReady(true);
      }
    }
  }, []);

  return { authUser, authReady, authenticate, endSession };
}
