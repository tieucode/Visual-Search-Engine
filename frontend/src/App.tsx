import { Activity } from 'lucide-react';
import { Link, Navigate, Outlet, Route, BrowserRouter, Routes } from 'react-router-dom';
import { ToastProvider } from './components/common/Toast';
import { AppHeader } from './components/layout/AppHeader';
import { AuthPage } from './pages/AuthPage';
import { UploadPage } from './pages/UploadPage';
import { getAccessToken } from './services/authToken';
import { SearchWorkspace } from './components/search/SearchWorkspace';
import { SearchPage } from './pages/SearchPage/SearchPage';
import { ImageDetailPage } from './pages/SearchPage/ImageDetailPage';
import { UploadHistoryPage } from "./pages/UploadHistoryPage";

function ProtectedLayout() {
  return getAccessToken() ? (
    <SearchWorkspace>
      <AppHeader />
      <Outlet />
    </SearchWorkspace>
  ) : <Navigate to="/login" replace />;
}

function GuestAuth({ mode }: { mode: 'login' | 'register' }) {
  return getAccessToken() ? <Navigate to="/" replace /> : <AuthPage key={mode} mode={mode} />;
}

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<ProtectedLayout />}>
            <Route path="/" element={<SearchPage />} />
            <Route path="/images/:imageId" element={<ImageDetailPage />} />
            <Route path="/upload" element={<UploadPage />} />
            <Route path="/upload/history" element={<UploadHistoryPage />} />
            <Route
              path="/batches"
              element={
                <main className="app-placeholder page-container">
                  <div>
                    <span className="app-placeholder__icon"><Activity size={22} aria-hidden="true" /></span>
                    <h1>Tiến trình xử lý</h1>
                    <p>Theo dõi các ảnh đang xử lý tại trang tải ảnh.</p>
                    <Link to="/upload" className="button button--outline">Xem tiến trình</Link>
                  </div>
                </main>
              }
            />
          </Route>
          <Route path="/login" element={<GuestAuth mode="login" />} />
          <Route path="/register" element={<GuestAuth mode="register" />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
}
