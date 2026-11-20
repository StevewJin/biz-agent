#!/usr/bin/env node
/**
 * 打包扩展包：manifest.json + ui/entry.js → chart_pro.bpk (zip)
 *
 * 用法：npm run pack
 * 产物：dist/chart_pro.bpk，可直接在「扩展包管理」页面导入
 */
import { execFileSync } from 'node:child_process';
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const manifest = JSON.parse(readFileSync(join(root, 'manifest.json'), 'utf8'));
const staging = join(root, 'dist', 'staging');
const output = join(root, 'dist', `${manifest.code}.bpk`);

rmSync(join(root, 'dist'), { recursive: true, force: true });
mkdirSync(staging, { recursive: true });

writeFileSync(join(staging, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf8');
cpSync(join(root, 'ui'), join(staging, 'ui'), { recursive: true });

// 使用系统 zip 命令（execFile 避免 shell 注入）；Windows 可替换为 Compress-Archive
execFileSync('zip', ['-qr', output, '.'], { cwd: staging, stdio: 'inherit' });

console.log(`扩展包已生成: ${output}`);
console.log('在 BizAgent 管理控制台 → 扩展包 → 导入扩展包 上传该文件');
