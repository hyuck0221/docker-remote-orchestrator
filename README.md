<p align="center">
  <img src="desktop/src/main/resources/icons/icon.png" width="128" height="128" alt="Travel Plan Logo" />
</p>

<h1 align="center">Docker Remote Orchestrator</h1>

<p align="center">
  A cross-platform desktop application for managing Docker containers across multiple remote machines from a single dashboard.<br>
</p>

---

[한국어](README_KO.md)

## Features

- **Host Mode**: Start a server and share a host code with remote nodes
- **Client Mode**: Connect to a host by entering the host code
- **Live Dashboard**: Monitor containers across all connected nodes in real-time
- **Container Control**: Start, stop, restart, and remove containers remotely
- **Auto Update**: Checks for new versions on startup
- **TLS Support**: Optional encrypted communication between host and clients
- **Webhook Integration**: HTTP webhook notifications for container events

## Architecture

```
┌──────────┐     WebSocket     ┌──────────┐
│  Desktop │◄─────────────────►│  Server  │
│   (UI)   │                   │  (Ktor)  │
└──────────┘                   └────┬─────┘
                                    │
                          ┌─────────┼─────────┐
                          ▼         ▼         ▼
                      ┌───────┐ ┌───────┐ ┌───────┐
                      │Client │ │Client │ │Client │
                      │Node 1 │ │Node 2 │ │Node 3 │
                      └───┬───┘ └───┬───┘ └───┬───┘
                          │         │         │
                        Docker    Docker    Docker
```

## Download

Download the latest release from the [Releases](https://github.com/shimhyuck/docker-remote-orchestrator/releases) page.

| Platform | Format |
|----------|--------|
| macOS (Apple Silicon) | `.dmg` |
| macOS (Intel) | `.dmg` |
| Windows (x64) | `.msi` |

### macOS - Gatekeeper Notice

Since the app is not signed with an Apple Developer certificate, macOS may block it. To open:

**Option 1**: Right-click the app → Select **Open** → Click **Open** in the dialog.

**Option 2**: Run in terminal after installation:
```bash
xattr -cr /Applications/DRO.app
```

**Option 3**: Go to **System Settings → Privacy & Security** → Click **Open Anyway**.

## Build from Source

### Prerequisites

- JDK 17+
- Docker (for client node functionality)

### Build

```bash
# Clone
git clone https://github.com/shimhyuck/docker-remote-orchestrator.git
cd docker-remote-orchestrator

# Run desktop app
./gradlew :desktop:run

# Package for current OS
./gradlew :desktop:packageDmg   # macOS
./gradlew :desktop:packageMsi   # Windows
./gradlew :desktop:packageDeb   # Linux
```

## Usage

### As Host (Server)

1. Launch DRO
2. Click **Start Host**
3. Share the generated **Host Code** with remote nodes
4. Monitor all connected nodes on the dashboard

### As Client (Remote Node)

1. Launch DRO
2. Click **Connect as Client**
3. Enter the **Host Code** and server address
4. The node's Docker containers will appear on the host dashboard

### Standalone Client (Headless)

```bash
./gradlew :client:run --args="<server-host> <port>"

# With TLS
./gradlew :client:run --args="--tls <server-host> <port>"
```

### Standalone Server

```bash
./gradlew :server:run

# With custom port and TLS
./gradlew :server:run --args="--port 9090 --tls"
```

## Tech Stack

- **UI**: Jetpack Compose Desktop (Material 3)
- **Server/Client**: Ktor (WebSocket)
- **Docker**: docker-java
- **Language**: Kotlin
- **Serialization**: kotlinx.serialization

## Project Structure

```
dro/
├── common/    # Shared models, protocols, security utilities
├── server/    # Ktor WebSocket server, session management
├── client/    # Docker integration, host connection
└── desktop/   # Compose Desktop UI
```

## License

[MIT](LICENSE)
