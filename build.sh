#!/bin/bash
#
# BizAgent 单体部署打包脚本
# 用法: ./build.sh
# 输出: backend/target/backend-1.0.0.jar (包含前端 + 后端的 fat JAR)
#

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
BACKEND_DIR="$PROJECT_ROOT/backend"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"
BUNDLED_JDK=""
for candidate in "$PROJECT_ROOT"/jdk-*/Contents/Home; do
  if [ -d "$candidate" ]; then BUNDLED_JDK="$candidate"; break; fi
done
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# 优先使用项目自带 JDK，否则回退到系统 JDK
if [ -n "$BUNDLED_JDK" ] && [ -d "$BUNDLED_JDK" ]; then
  export JAVA_HOME="$BUNDLED_JDK"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  BizAgent 单体部署打包${NC}"
echo -e "${BLUE}  ${TIMESTAMP}${NC}"
echo -e "${BLUE}========================================${NC}"

# --------------- 1. 构建前端 ---------------
echo -e "${GREEN}[1/3] 构建前端...${NC}"
cd "$FRONTEND_DIR"

if [ ! -d "node_modules" ]; then
  echo "安装前端依赖..."
  npm install
fi

npm run build

if [ ! -d "dist" ]; then
  echo "错误: 前端构建失败，dist 目录不存在"
  exit 1
fi

# --------------- 2. 复制前端静态文件到 Spring Boot ---------------
echo -e "${GREEN}[2/3] 复制前端文件到后端静态资源目录...${NC}"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$FRONTEND_DIR/dist/"* "$STATIC_DIR/"
echo "已复制 $(ls -1 "$STATIC_DIR" | wc -l) 个文件/目录到 $STATIC_DIR"

# --------------- 3. 构建后端 fat JAR ---------------
echo -e "${GREEN}[3/3] 构建后端 fat JAR...${NC}"
cd "$PROJECT_ROOT"

# 多模块构建：extension-api 需与 backend 一起构建（-pl backend -am）
./backend/mvnw clean package -DskipTests -q -f pom.xml -pl backend -am

JAR_FILE="$BACKEND_DIR/target/backend-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "错误: JAR 构建失败"
  exit 1
fi

JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}  打包完成!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "  输出文件: $JAR_FILE"
echo "  文件大小: $JAR_SIZE"
echo ""
echo "  启动命令:"
echo "    ./start.sh"
echo ""
echo "  或直接使用 java:"
echo "    java -jar $JAR_FILE"
echo ""
echo -e "${BLUE}========================================${NC}"
