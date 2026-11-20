import {
  BarChart, Bar, LineChart, Line, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts';
import type { PieLabelRenderProps } from 'recharts';
import type { ChartData } from '../../types';

const COLORS = ['#8884d8', '#82ca9d', '#ffc658', '#ff7300', '#0088fe', '#00c49f', '#ffbb28', '#ff8042'];

interface ChartRendererProps {
  data: ChartData;
}

/** 将 labels+datasets 转为 recharts 需要的格式 [{name, series1, series2}, ...] */
function transformData(chartData: ChartData) {
  return chartData.labels.map((label, i) => {
    const row: Record<string, string | number> = { name: label };
    chartData.datasets.forEach((ds) => {
      row[ds.label] = ds.data[i] ?? 0;
    });
    return row;
  });
}

export default function ChartRenderer({ data }: ChartRendererProps) {
  const { chartType, title, datasets, labels } = data;

  if (!labels || labels.length === 0) {
    return <div className="text-xs text-gray-400">图表数据为空</div>;
  }

  return (
    <div className="my-2">
      {title && (
        <h4 className="text-sm font-medium text-gray-700 text-center mb-2">{title}</h4>
      )}
      <ResponsiveContainer width="100%" height={280}>
        {chartType === 'pie' ? (
          <PieChart>
            <Pie
              data={labels.map((name, i) => ({
                name,
                value: datasets[0]?.data[i] ?? 0
              }))}
              cx="50%"
              cy="50%"
              outerRadius={100}
              dataKey="value"
              label={(entry: PieLabelRenderProps) =>
                `${entry.name ?? ''} ${((entry.percent ?? 0) * 100).toFixed(0)}%`
              }
            >
              {labels.map((_, i) => (
                <Cell key={i} fill={COLORS[i % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        ) : chartType === 'line' ? (
          <LineChart data={transformData(data)}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis dataKey="name" tick={{ fontSize: 12 }} />
            <YAxis tick={{ fontSize: 12 }} />
            <Tooltip />
            <Legend />
            {datasets.map((ds, i) => (
              <Line
                key={ds.label}
                type="monotone"
                dataKey={ds.label}
                stroke={COLORS[i % COLORS.length]}
                strokeWidth={2}
                dot={{ r: 3 }}
                activeDot={{ r: 5 }}
              />
            ))}
          </LineChart>
        ) : (
          <BarChart data={transformData(data)}>
            <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
            <XAxis dataKey="name" tick={{ fontSize: 12 }} />
            <YAxis tick={{ fontSize: 12 }} />
            <Tooltip />
            <Legend />
            {datasets.map((ds, i) => (
              <Bar
                key={ds.label}
                dataKey={ds.label}
                fill={COLORS[i % COLORS.length]}
                radius={[4, 4, 0, 0]}
              />
            ))}
          </BarChart>
        )}
      </ResponsiveContainer>
    </div>
  );
}
