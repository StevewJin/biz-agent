#!/bin/bash
#
# BizAgent 启动脚本（优先使用仓库内置 JDK，其次 JAVA_HOME，最后 PATH 中的 java）
# 用法: ./start.sh        （会读取项目根目录的 .env）
#

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# 载入 .env（若有）
if [ -f "$PROJECT_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$PROJECT_ROOT/.env"
  set +a
fi

BUNDLED_JDK=""
for candidate in "$PROJECT_ROOT"/jdk-*/Contents/Home "$PROJECT_ROOT"/jdk-*; do
  if [ -x "$candidate/bin/java" ]; then BUNDLED_JDK="$candidate"; break; fi
done

if [ -n "$BUNDLED_JDK" ]; then
  JAVA="$BUNDLED_JDK/bin/java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java || true)"
fi

JAR_FILE="$PROJECT_ROOT/backend/target/backend-1.0.0.jar"

if [ -z "$JAVA" ] || [ ! -x "$JAVA" ]; then
  echo "错误: 未找到 Java。请安装 JDK 22+ 并配置 JAVA_HOME，或把 JDK 解压到项目根目录（jdk-*）"
  exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
  echo "错误: 未找到 JAR 包，请先执行 ./build.sh 构建项目"
  exit 1
fi

echo "========================================"
echo "  BizAgent 启动中..."
echo "  JDK: $("$JAVA" -version 2>&1 | head -1)"
echo "  端口: ${SERVER_PORT:-8080}"
echo "========================================"

exec "$JAVA" -jar "$JAR_FILE" \
  --server.port="${SERVER_PORT:-8080}" \
  "$@"
