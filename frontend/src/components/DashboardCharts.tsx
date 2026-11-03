import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Clock3, Flag, TrendingUp } from "lucide-react";
import type { Goal, GoalStatus } from "../types";
import { EmptyState, formatLabel } from "./ui";

const statusColors: Record<GoalStatus, string> = {
  NOT_STARTED: "#64748b",
  IN_PROGRESS: "#8b5cf6",
  COMPLETED: "#6ee7b7",
  CANCELLED: "#475569",
};

export interface WeeklyStudyDay {
  date: string;
  label: string;
  minutes: number;
}

export function DashboardCharts({
  goals,
  weekly,
  monthly,
  weeklyProgress,
}: {
  goals: Goal[];
  weekly: number;
  monthly: number;
  weeklyProgress: WeeklyStudyDay[];
}) {
  const statuses: GoalStatus[] = ["IN_PROGRESS", "NOT_STARTED", "COMPLETED", "CANCELLED"];
  const statusData = statuses.map((status) => ({
    name: formatLabel(status),
    value: goals.filter((goal) => goal.status === status).length,
    fill: statusColors[status],
  }));
  const chartData = [
    { name: "This week", minutes: weekly },
    { name: "This month", minutes: monthly },
  ];

  return (
    <section className="dashboard-grid">
      <div className="panel chart-panel">
        <div className="panel-heading">
          <div><h2>Goal status</h2><p>Your goals, by where they are today</p></div>
          <span className="panel-icon"><Flag size={17} /></span>
        </div>
        {goals.length === 0 ? (
          <EmptyState icon={<Flag size={22} />} title="Your goals start here" description="Create a goal to give your learning a direction." />
        ) : (
          <div className="status-chart-wrap">
            <div className="donut-wrap">
              <ResponsiveContainer width="100%" height={210}>
                <PieChart>
                  <Pie data={statusData} dataKey="value" nameKey="name" innerRadius={65} outerRadius={88} paddingAngle={3} stroke="none">
                    {statusData.map((entry) => <Cell key={entry.name} fill={entry.fill} />)}
                  </Pie>
                  <Tooltip formatter={(value) => [`${value ?? 0} goals`, "Goals"]} />
                </PieChart>
              </ResponsiveContainer>
              <div className="donut-center"><strong>{goals.length}</strong><span>goals</span></div>
            </div>
            <div className="legend-list">
              {statusData.map((entry) => (
                <div className="legend-item" key={entry.name}>
                  <span className="legend-dot" style={{ backgroundColor: entry.fill }} />
                  <span>{entry.name}</span><strong>{entry.value}</strong>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
      <div className="panel chart-panel">
        <div className="panel-heading">
          <div><h2>Time in focus</h2><p>Recorded minutes by period</p></div>
          <span className="panel-icon"><Clock3 size={17} /></span>
        </div>
        {goals.length === 0 ? (
          <EmptyState icon={<Clock3 size={22} />} title="No activity yet" description="Log a completed session to see your time here." />
        ) : (
          <div className="activity-chart">
            <ResponsiveContainer width="100%" height={230}>
              <BarChart data={chartData} margin={{ top: 10, right: 12, left: -18, bottom: 0 }}>
                <CartesianGrid stroke="rgb(167 139 250 / 15%)" vertical={false} />
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: "#a5b4c7", fontSize: 12 }} />
                <YAxis axisLine={false} tickLine={false} tick={{ fill: "#a5b4c7", fontSize: 11 }} />
                <Tooltip
                  cursor={{ fill: "rgb(167 139 250 / 9%)" }}
                  formatter={(value) => [`${value ?? 0} minutes`, "Study time"]}
                  contentStyle={{ borderRadius: 12, border: "1px solid rgb(167 139 250 / 24%)", color: "#f8fafc", background: "#202b43", boxShadow: "0 12px 32px rgb(3 8 20 / 40%)" }}
                />
                <Bar dataKey="minutes" fill="#8b5cf6" radius={[7, 7, 2, 2]} maxBarSize={56} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
      <div className="panel chart-panel weekly-progress-panel">
        <div className="panel-heading">
          <div><h2>Weekly study progress</h2><p>Minutes logged each day this week</p></div>
          <span className="panel-icon"><TrendingUp size={17} /></span>
        </div>
        {weeklyProgress.every((day) => day.minutes === 0) ? (
          <EmptyState icon={<Clock3 size={22} />} title="Your week is open" description="Log a study session to see your daily progress here." />
        ) : (
          <div className="activity-chart">
            <ResponsiveContainer width="100%" height={230}>
              <BarChart data={weeklyProgress} margin={{ top: 10, right: 12, left: -18, bottom: 0 }}>
                <CartesianGrid stroke="rgb(167 139 250 / 15%)" vertical={false} />
                <XAxis dataKey="label" axisLine={false} tickLine={false} tick={{ fill: "#a5b4c7", fontSize: 12 }} />
                <YAxis axisLine={false} tickLine={false} tick={{ fill: "#a5b4c7", fontSize: 11 }} />
                <Tooltip
                  cursor={{ fill: "rgb(167 139 250 / 9%)" }}
                  labelFormatter={(_label, entries) => entries[0]?.payload.date ?? ""}
                  formatter={(value) => [`${value ?? 0} minutes`, "Study time"]}
                  contentStyle={{ borderRadius: 12, border: "1px solid rgb(167 139 250 / 24%)", color: "#f8fafc", background: "#202b43", boxShadow: "0 12px 32px rgb(3 8 20 / 40%)" }}
                />
                <Bar dataKey="minutes" fill="#c084fc" radius={[7, 7, 2, 2]} maxBarSize={56} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </section>
  );
}
