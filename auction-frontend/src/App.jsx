import React, { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppProvider } from './context/AppContext';
import ErrorBoundary from './components/ErrorBoundary';

// Layouts
import MainLayout from './layouts/MainLayout';

// Pages
const LandingPage = lazy(() => import('./pages/LandingPage'));
const RoleSelection = lazy(() => import('./pages/RoleSelection'));
const AdminLogin = lazy(() => import('./pages/AdminLogin'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const PlayersPage = lazy(() => import('./pages/PlayersPage'));
const TeamsPage = lazy(() => import('./pages/TeamsPage'));
const AuctionPage = lazy(() => import('./pages/AuctionPage'));
const StatisticsPage = lazy(() => import('./pages/StatisticsPage'));
const BiddingRulesPage = lazy(() => import('./pages/BiddingRulesPage'));

function App() {
  return (
    <ErrorBoundary>
    <AppProvider>
      <BrowserRouter>
        <Suspense fallback={<div role="status" style={{ padding: 32 }}>Loading...</div>}>
        <Routes>
          {/* Public Landing Pages */}
          <Route path="/" element={<LandingPage />} />
          <Route path="/role/:city" element={<RoleSelection />} />
          <Route path="/login/:city" element={<AdminLogin />} />

          {/* Nested Dashboard Routes */}
          <Route path="/dashboard/:city/:role" element={<MainLayout />}>
            <Route index element={<Dashboard />} />
            <Route path="players" element={<PlayersPage />} />
            <Route path="teams" element={<TeamsPage />} />
            <Route path="auction" element={<AuctionPage />} />
            <Route path="statistics" element={<StatisticsPage />} />
            <Route path="rules" element={<BiddingRulesPage />} />
          </Route>

          {/* Catch-all Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
      </BrowserRouter>
    </AppProvider>
    </ErrorBoundary>
  );
}

export default App;
