import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from './lib/theme';
import { UserProvider } from './lib/user-context';
import { ToastProvider } from './lib/toast';
import { UpgradeRequestProvider } from './lib/upgrade-requests';
import App from './App';
import './styles.css';

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <UserProvider>
        <ToastProvider>
          <UpgradeRequestProvider>
            <ThemeProvider>
              <App />
            </ThemeProvider>
          </UpgradeRequestProvider>
        </ToastProvider>
      </UserProvider>
    </QueryClientProvider>
  </React.StrictMode>
);
