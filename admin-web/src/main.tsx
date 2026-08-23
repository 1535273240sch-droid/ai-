import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import 'antd/dist/reset.css';
import './styles/glass.css';
import App from './App';

const appleTheme = {
  token: {
    colorPrimary: '#0071e3',
    colorInfo: '#0071e3',
    colorLink: '#0071e3',
    colorSuccess: '#34c759',
    colorWarning: '#ff9f0a',
    colorError: '#ff3b30',
    borderRadius: 14,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    colorBgContainer: 'rgba(255,255,255,0.72)',
    colorBgElevated: 'rgba(255,255,255,0.88)',
    colorBorder: 'rgba(255,255,255,0.6)',
    boxShadow:
      '0 8px 32px rgba(31, 38, 135, 0.12)',
  },
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={appleTheme}>
      <AntApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>
);
