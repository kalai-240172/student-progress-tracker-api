import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { LoadingState } from "./components/ui";
import { useAuth } from "./auth/AuthContext";

const AuthPage = lazy(() => import("./pages/AuthPage").then((module) => ({ default: module.AuthPage })));
const DashboardPage = lazy(() => import("./pages/DashboardPage").then((module) => ({ default: module.DashboardPage })));
const RecordsPage = lazy(() => import("./pages/RecordsPage").then((module) => ({ default: module.RecordsPage })));
const RecordFormPage = lazy(() => import("./pages/RecordsPage").then((module) => ({ default: module.RecordFormPage })));
const RecordDetailPage = lazy(() => import("./pages/RecordsPage").then((module) => ({ default: module.RecordDetailPage })));
const GoalsPage = lazy(() => import("./pages/GoalsPage").then((module) => ({ default: module.GoalsPage })));
const GoalFormPage = lazy(() => import("./pages/GoalsPage").then((module) => ({ default: module.GoalFormPage })));
const GoalDetailPage = lazy(() => import("./pages/GoalsPage").then((module) => ({ default: module.GoalDetailPage })));
const TasksPage = lazy(() => import("./pages/TasksPage").then((module) => ({ default: module.TasksPage })));
const CalendarPage = lazy(() => import("./pages/CalendarPage").then((module) => ({ default: module.CalendarPage })));
const StudyHistoryPage = lazy(() => import("./pages/StudyHistoryPage").then((module) => ({ default: module.StudyHistoryPage })));

function RequireAuth() {
  const { isAuthenticated, isChecking } = useAuth();
  const location = useLocation();
  if (isChecking) return <LoadingState />;
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <AppShell />;
}

function NotFoundPage() {
  return (
    <div className="not-found">
      <span>404</span>
      <h1>We couldn’t find that page</h1>
      <p>The address may have changed, or the page may not exist.</p>
      <a className="button button-primary" href="/">Back to overview</a>
    </div>
  );
}

export default function App() {
  return (
    <Suspense fallback={<LoadingState />}>
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
        <Route element={<RequireAuth />}>
          <Route index element={<DashboardPage />} />
          <Route path="/records" element={<RecordsPage />} />
          <Route path="/records/new" element={<RecordFormPage />} />
          <Route path="/records/:id" element={<RecordDetailPage />} />
          <Route path="/records/:id/edit" element={<RecordFormPage />} />
          <Route path="/goals" element={<GoalsPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/history" element={<StudyHistoryPage />} />
          <Route path="/goals/new" element={<GoalFormPage />} />
          <Route path="/goals/:id" element={<GoalDetailPage />} />
          <Route path="/goals/:id/edit" element={<GoalFormPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
