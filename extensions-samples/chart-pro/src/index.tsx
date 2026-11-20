import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Legend,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';

interface Dataset {
  label: string;
  data: number[];
}

interface RadarProps {
  title?: string;
  labels?: string[];
  datasets?: Dataset[];
}

const COLORS = ['#7c3aed', '#0ea5e9', '#f59e0b', '#10b981', '#ef4444'];

function RadarRenderer(props: Record<string, unknown>) {
  const { title, labels, datasets } = props as RadarProps;
  const safeLabels = labels || [];
  const safeDatasets = datasets || [];

  if (safeLabels.length === 0 || safeDatasets.length === 0) {
    return <div style={{ fontSize: 12, color: '#9ca3af' }}>雷达图数据为空</div>;
  }

  const data = safeLabels.map((label, index) => {
    const row: Record<string, string | number> = { name: label };
    safeDatasets.forEach((dataset) => {
      row[dataset.label] = dataset.data[index] ?? 0;
    });
    return row;
  });

  return (
    <div>
      {title && (
        <h4 style={{ fontSize: 14, fontWeight: 500, color: '#374151', textAlign: 'center', marginBottom: 8 }}>
          {title}
        </h4>
      )}
      <ResponsiveContainer width="100%" height={300}>
        <RadarChart data={data} outerRadius="75%">
          <PolarGrid />
          <PolarAngleAxis dataKey="name" tick={{ fontSize: 11 }} />
          <PolarRadiusAxis tick={{ fontSize: 10 }} />
          <Tooltip />
          <Legend />
          {safeDatasets.map((dataset, index) => (
            <Radar
              key={dataset.label}
              name={dataset.label}
              dataKey={dataset.label}
              stroke={COLORS[index % COLORS.length]}
              fill={COLORS[index % COLORS.length]}
              fillOpacity={0.25}
            />
          ))}
        </RadarChart>
      </ResponsiveContainer>
    </div>
  );
}

/**
 * 扩展包入口：平台加载 entry.js 后会调用 activate(host)，
 * 返回的渲染器会注册到宿主渲染表。
 */
function activate() {
  return {
    renderers: [
      {
        type: 'chart_pro.radar',
        render: (props: Record<string, unknown>) => RadarRenderer(props),
      },
    ],
  };
}

declare global {
  interface Window {
    __bizagentExt?: Record<string, { activate: () => unknown }>;
  }
}

window.__bizagentExt = window.__bizagentExt || {};
window.__bizagentExt['chart_pro'] = { activate };
