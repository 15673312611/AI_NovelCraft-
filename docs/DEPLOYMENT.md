# 部署说明

本项目支持本地开发与 Docker Compose 部署。

## 环境变量
- 数据库：MYSQL_ROOT_PASSWORD、MYSQL_DATABASE（在 docker-compose.yml 中配置）
- OpenAI：OPENAI_API_KEY（放置于 .env 或部署平台 Secret）

## Docker Compose 启动
```bash
docker-compose up -d
```

- 前端：http://localhost:3000
- 后端：http://localhost:8080
- MySQL：localhost:3306
- Redis：localhost:6379

## 反向代理
- 使用 Nginx 将 /api 转发至 backend:8080
- 注意跨域与鉴权头透传（Authorization: Bearer <token>）

## 生产加固建议
1. 后端只暴露 8080（容器内），通过 Nginx/Gateway 统一出口
2. 开启 HTTPS（TLS 终止在 Nginx）
3. 只在 dev 启用 AITestController（@Profile("dev")）
4. 数据库与缓存使用专用网络与凭证旋转
5. 观测性：接入结构化日志、指标与告警（如 Prometheus + Grafana）

