#!/usr/bin/env bash
#
# 启用仓库内 Git hooks（提交前密钥扫描）
# 用法：./scripts/install-git-hooks.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ ! -d .git ]; then
  echo "✗ 当前目录还不是 Git 仓库，请先执行：git init -b main"
  exit 1
fi

if [ ! -f .githooks/pre-commit ]; then
  echo "✗ 缺少 .githooks/pre-commit"
  exit 1
fi

chmod +x .githooks/pre-commit
git config core.hooksPath .githooks

echo "✅ 已启用仓库内 Git hooks"
echo "   core.hooksPath = .githooks"
echo "   每次 git commit 前会扫描暂存内容中的密钥与敏感文件"
echo ""
echo "   临时跳过：git commit --no-verify"
echo "   关闭：    git config --unset core.hooksPath"
