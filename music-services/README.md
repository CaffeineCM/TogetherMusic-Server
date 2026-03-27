# Music Services

当前项目把 `网易云音乐 API` 作为本地侧车服务放在：

- `music-services/netease-api`

后端默认会从环境变量读取音乐服务地址：

- `NETEASE_API_BASE_URL`，默认 `http://localhost:3000`
- `QQ_MUSIC_API_BASE_URL`，默认 `http://localhost:3300`
- `KUGOU_API_BASE_URL`，默认 `http://localhost:3400`

本地开发推荐方式：

1. 启动网易云音乐 API

```bash
./scripts/start-netease-api.sh
```

2. 查看状态

```bash
./scripts/status-netease-api.sh
```

3. 停止服务

```bash
./scripts/stop-netease-api.sh
```

运行日志位置：

- `music-services/runtime/netease-api.log`

如果你要换端口，可在启动前设置：

```bash
export NETEASE_API_PORT=3001
export NETEASE_API_BASE_URL=http://localhost:3001
```

然后再启动当前脚本，并用 IDEA 启动后端。
