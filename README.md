# TogetherMusic Server

一起听歌 - 后端服务

## 技术栈

- **Java 21**
- **Spring Boot 3.5.0**
- **Spring WebSocket** - 实时通信
- **Spring Data JPA** - 数据持久化
- **Spring Data Redis** - 缓存
- **Spring Security** - 安全框架
- **Sa-Token** - 认证授权
- **PostgreSQL** - 数据库
- **Flyway** - 数据库迁移
- **Lombok** - 简化代码

## 环境要求

- JDK 21+
- PostgreSQL 14+
- Redis 6+
- Node.js 16+ (用于音乐 API 服务)

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/CaffeineCM/TogetherMusic-Server.git
cd TogetherMusic-Server
```

### 2. 配置数据库

修改 `src/main/resources/application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/together_music
    username: your_username
    password: your_password
```

### 3. 启动 Redis

```bash
redis-server
```

### 4. 启动音乐 API 服务

```bash
# 启动网易云音乐 API
./scripts/start-netease-api.sh

# 查看状态
./scripts/status-netease-api.sh

# 停止服务
./scripts/stop-netease-api.sh
```

> 详细说明请参考 [music-services/README.md](music-services/README.md)

### 5. 运行项目

```bash
./gradlew bootRun
```

或使用 IDE 运行 `TogetherMusicApplication.java`

## 配置说明

### 音乐 API 地址

可通过环境变量配置：

```bash
export NETEASE_API_BASE_URL=http://localhost:3000
export QQ_MUSIC_API_BASE_URL=http://localhost:3300
export KUGOU_API_BASE_URL=http://localhost:3400
```

### 文件上传

默认配置：
- 存储路径: `/data/tm-uploads`
- 最大文件: 100MB
- 支持格式: mp3, flac, wav, ogg, mp4

### 房间配置

- 单 IP 房间上限: 3
- 房间最大人数: 32
- 歌曲投票通过率: 30%

## 项目结构

```
src/main/java/com/togethermusic/
├── auth/          # 认证模块
├── chat/          # 聊天模块
├── common/        # 公共组件
├── config/        # 配置类
├── dev/           # 开发工具
├── job/           # 定时任务
├── music/         # 音乐核心模块
├── room/          # 房间模块
├── upload/        # 文件上传
└── user/          # 用户模块
```

## 构建部署

```bash
# 构建 JAR
./gradlew build

# 产物位置
# build/libs/together-music-backend.jar

# 运行
java -jar build/libs/together-music-backend.jar
```

## License

MIT
