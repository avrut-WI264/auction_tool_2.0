import { apiFetch } from '../../api/api.js';

const resource = (city) => `/api/${encodeURIComponent(city)}/auction`;
const post = (path, body) => apiFetch(path, { method: 'POST', body: JSON.stringify(body) });

/** HTTP details stay outside React state and presentation components. */
export const auctionApi = {
  state: (city) => apiFetch(`${resource(city)}/state`),
  revision: (city) => apiFetch(`${resource(city)}/revision`),
  start: (city, lot) => post(`${resource(city)}/start`, lot),
  bid: (city, command) => post(`${resource(city)}/bid`, command),
  revert: (city, command) => post(`${resource(city)}/revert`, command),
  pass: (city, command) => post(`${resource(city)}/pass`, command),
  next: (city, command) => post(`${resource(city)}/next`, command),
  pause: (city, command) => post(`${resource(city)}/pause`, command),
  resume: (city, command) => post(`${resource(city)}/resume`, command),
  sell: (city, command) => post(`${resource(city)}/sell`, command),
  reAuction: (city, lot, revision) => post(`${resource(city)}/re-auction?${new URLSearchParams(lot)}`, { revision }),
};
