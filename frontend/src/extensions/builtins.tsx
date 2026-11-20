import { useState } from 'react';
import { Download, FileSpreadsheet, FileText } from 'lucide-react';
import { downloadFile, getDownloadUrl } from '../services/api';
import ChartRenderer from '../components/chat/ChartRenderer';
import type { ChartData } from '../types';
import { registerRenderer } from './registry';

/**
 * 内置渲染器：chart / table / export / json。
 *
 * 扩展包可复用这些类型，也可以注册自己的类型（如 chart_pro.chart）。
 */

interface TableProps {
  title?: string;
  columns?: string[];
  rows?: Array<Record<string, unknown>>;
  total?: number;
}

function TableRenderer(props: Record<string, unknown>) {
  const { title, columns, rows, total } = props as TableProps;
  const cols = columns && columns.length > 0
    ? columns
    : rows && rows.length > 0
      ? Object.keys(rows[0])
      : [];

  if (!rows || rows.length === 0) {
    return <div className="text-xs text-gray-400">无数据</div>;
  }

  return (
    <div className="my-2">
      {title && <h4 className="text-sm font-medium text-gray-700 mb-2">{title}</h4>}
      <div className="overflow-x-auto border border-gray-200 rounded-lg">
        <table className="min-w-full text-xs">
          <thead className="bg-gray-50 text-gray-500">
            <tr>
              {cols.map((col) => (
                <th key={col} className="px-3 py-2 text-left font-medium whitespace-nowrap">{col}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.slice(0, 100).map((row, index) => (
              <tr key={index} className="hover:bg-gray-50">
                {cols.map((col) => (
                  <td key={col} className="px-3 py-1.5 text-gray-700 whitespace-nowrap">
                    {row[col] === null || row[col] === undefined ? '' : String(row[col])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="text-[11px] text-gray-400 mt-1">
        共 {total ?? rows.length} 行{rows.length > 100 ? '（仅展示前 100 行）' : ''}
      </div>
    </div>
  );
}

interface ExportProps {
  downloadUrl: string;
  filename: string;
  format: string;
  rowCount: number;
  fileSizeFormatted: string;
}

function ExportRenderer(props: Record<string, unknown>) {
  const data = props as unknown as ExportProps;
  const [downloading, setDownloading] = useState(false);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await downloadFile(getDownloadUrl(data.downloadUrl), data.filename);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="flex items-center gap-3 bg-white rounded-lg border border-green-200 p-3 my-2">
      {data.format === 'csv'
        ? <FileText className="w-8 h-8 text-green-500 shrink-0" />
        : <FileSpreadsheet className="w-8 h-8 text-green-600 shrink-0" />}
      <div className="flex-1 min-w-0">
        <div className="text-sm font-medium text-gray-800 truncate">{data.filename}</div>
        <div className="text-xs text-gray-400 mt-0.5">{data.rowCount} 行 · {data.fileSizeFormatted}</div>
      </div>
      <button
        onClick={handleDownload}
        disabled={downloading}
        className="flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-white bg-green-600 hover:bg-green-700 rounded-lg cursor-pointer disabled:opacity-60 shrink-0 transition"
      >
        <Download size={14} />
        {downloading ? '下载中...' : '下载'}
      </button>
    </div>
  );
}

function JsonRenderer(props: Record<string, unknown>) {
  return (
    <pre className="text-xs bg-white/60 rounded p-2 overflow-x-auto whitespace-pre-wrap break-all text-gray-600 max-h-64 overflow-y-auto">
      {JSON.stringify(props, null, 2)}
    </pre>
  );
}

/** 注册内置渲染器（幂等） */
export function registerBuiltinRenderers(): void {
  registerRenderer('chart', (props) => <ChartRenderer data={props as unknown as ChartData} />);
  registerRenderer('table', (props) => <TableRenderer {...props} />);
  registerRenderer('export', (props) => <ExportRenderer {...props} />);
  registerRenderer('json', (props) => <JsonRenderer {...props} />);
}
