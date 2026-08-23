import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { getToken } from './api/client';
import Layout from './components/Layout';
import Login from './pages/Login';

// 按页懒加载，降低首屏 chunk 体积（审查建议）
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Contacts = lazy(() => import('./pages/Contacts'));
const Personas = lazy(() => import('./pages/Personas'));
const Licenses = lazy(() => import('./pages/Licenses'));
const Logs = lazy(() => import('./pages/Logs'));

/** 路由守卫：未登录跳转 /login */
function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();
  if (!getToken()) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

function PageLoader({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      }
    >
      {children}
    </Suspense>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<PageLoader><Dashboard /></PageLoader>} />
        <Route path="contacts" element={<PageLoader><Contacts /></PageLoader>} />
        <Route path="personas" element={<PageLoader><Personas /></PageLoader>} />
        <Route path="licenses" element={<PageLoader><Licenses /></PageLoader>} />
        <Route path="logs" element={<PageLoader><Logs /></PageLoader>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
