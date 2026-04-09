package com.orchestrator.desktop.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val label: String) {
    EN("English"), KO("한국어")
}

val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }

fun AppLanguage.toStrings(): Strings = when (this) {
    AppLanguage.EN -> EnStrings
    AppLanguage.KO -> KoStrings
}

interface Strings {
    // ── Common ──
    val appTitle: String
    val appSubtitle: String
    val cancel: String
    val save: String
    val close: String
    val open: String
    val start: String
    val stop: String
    val connect: String
    val disconnect: String
    val done: String
    val settings: String
    val download: String
    val dismiss: String

    // ── Home Screen ──
    val startAsHost: String
    val joinAsClient: String
    val hostServerRunning: String
    val connectedAsClient: String
    fun hostCodeNodes(code: String, count: Int): String
    fun statusLabel(status: String): String

    // ── Host Dialog ──
    val startHost: String
    val port: String
    val externalAccess: String
    val allowExternalConnections: String
    val ngrokNotInstalled: String
    val ngrokNotConfigured: String
    val checkingNgrok: String
    val setup: String

    // ── ngrok Setup Dialog ──
    val ngrokSetup: String
    val ngrokNotInstalledDesc: String
    val installSteps: String
    val orDownloadFrom: String
    val afterInstallingReopen: String
    val ngrokNeedsToken: String
    val ngrokTokenInstructions: String
    val authToken: String
    val tokenSaved: String
    val tokenSaveFailed: String
    val ngrokReady: String
    val checkingNgrokStatus: String
    val saveToken: String
    val saving: String

    // ── Client Dialog ──
    val connectToHost: String
    val address: String
    val ngrokUrl: String
    val addressPlaceholder: String
    val ngrokDetected: String
    val hostCode: String

    // ── Tabs ──
    val tabMyNode: String
    fun tabNodes(count: Int): String
    val tabSettings: String

    // ── Host Dashboard ──
    val host: String
    val client: String
    fun runningOf(running: Int, total: Int): String
    fun nodesCount(count: Int): String
    val codeCopied: String
    val codeLabel: String

    // ── Container Card ──
    val containerRunning: String
    val containerExited: String
    val containerPaused: String
    val containerDead: String
    val containerCreated: String

    // ── Node Card ──
    val noContainers: String
    val fullControl: String
    val readOnly: String
    val denied: String

    // ── Deploy Dialog ──
    val deployContainer: String
    val sourceContainer: String
    val deployMode: String
    val instant: String
    val approval: String
    val instantDesc: String
    val approvalDesc: String
    val targetNodes: String
    val noRemoteNodes: String
    val options: String
    val containerName: String
    val environmentLabel: String
    val volumesLabel: String
    val deploy: String

    // ── Group Deploy Dialog ──
    val deployGroup: String
    val project: String
    fun containersCount(count: Int): String
    fun deployAll(count: Int): String

    // ── Deploy Notification Dialog ──
    val deployRequest: String
    fun deployRequestMessage(hostName: String): String
    val later: String
    val accept: String
    val image: String
    val name: String
    val ports: String
    val env: String
    val volumes: String

    // ── Pending Deploys ──
    fun pendingDeploys(count: Int): String
    fun deployRequestFrom(hostName: String): String

    // ── Container Group Header ──
    val group: String

    // ── Log Panel ──
    fun logsFor(name: String): String

    // ── Update Banner ──
    fun updateAvailable(version: String): String

    // ── Settings ──
    val displayName: String
    val displayNameDesc: String
    val nameLabel: String
    val namePlaceholder: String
    val launchAtStartup: String
    val launchAtStartupDesc: String
    val version: String
    fun currentVersion(v: String): String
    fun newVersionAvailable(v: String): String
    val upToDate: String
    val checkForUpdates: String
    val checking: String
    val language: String
    val languageDesc: String

    // ── Settings (Host) ──
    val general: String
    val defaultPermission: String
    val defaultPermissionDesc: String
    val wsPingInterval: String
    val wsPingIntervalDesc: String

    // ── Menu ──
    val menuSettings: String
    val menuPreferences: String

    // ── Status Messages (ViewModel) ──
    val startingServer: String
    fun serverRunningOnPort(port: Int): String
    val serverStopped: String
    val dockerNotAvailable: String
    val commandSent: String
    val commandSendFailed: String
    fun connectingTo(host: String, port: Int): String
    val connectionFailed: String
    val connectionRejected: String
    val connectedToServer: String
    val disconnected: String
    fun instantDeploySent(image: String): String
    fun approvalDeploySent(image: String): String
    fun deployToNodeFailed(nodeId: String): String
    val deploySuccessful: String
    fun deployFailed(msg: String): String
    fun deployingLocally(image: String): String
    val dockerNotAvailableForDeploy: String
    fun deployPhaseStatus(phase: String, msg: String): String
}

// ── English Implementation ──

object EnStrings : Strings {
    // ── Common ──
    override val appTitle = "Docker Remote\nOrchestrator"
    override val appSubtitle = "Manage containers across your network"
    override val cancel = "Cancel"
    override val save = "Save"
    override val close = "Close"
    override val open = "Open"
    override val start = "Start"
    override val stop = "Stop"
    override val connect = "Connect"
    override val disconnect = "Disconnect"
    override val done = "Done"
    override val settings = "Settings"
    override val download = "Download"
    override val dismiss = "Dismiss"

    // ── Home Screen ──
    override val startAsHost = "Start as Host"
    override val joinAsClient = "Join as Client"
    override val hostServerRunning = "Host Server Running"
    override val connectedAsClient = "Connected as Client"
    override fun hostCodeNodes(code: String, count: Int) = "Code: $code  ·  $count node(s)"
    override fun statusLabel(status: String) = "Status: $status"

    // ── Host Dialog ──
    override val startHost = "Start Host"
    override val port = "Port"
    override val externalAccess = "External Access (ngrok)"
    override val allowExternalConnections = "Allow connections from outside your network"
    override val ngrokNotInstalled = "ngrok not installed"
    override val ngrokNotConfigured = "ngrok auth token not set"
    override val checkingNgrok = "Checking ngrok..."
    override val setup = "Setup"

    // ── ngrok Setup Dialog ──
    override val ngrokSetup = "ngrok Setup"
    override val ngrokNotInstalledDesc = "ngrok is not installed."
    override val installSteps = "Install steps:"
    override val orDownloadFrom = "Or download from https://ngrok.com/download"
    override val afterInstallingReopen = "After installing, reopen this dialog."
    override val ngrokNeedsToken = "ngrok is installed but needs an auth token."
    override val ngrokTokenInstructions = "1. Sign up at https://ngrok.com\n2. Copy your authtoken from the dashboard\n3. Paste it below:"
    override val authToken = "Auth Token"
    override val tokenSaved = "Token saved successfully!"
    override val tokenSaveFailed = "Failed to save token."
    override val ngrokReady = "ngrok is ready to use!"
    override val checkingNgrokStatus = "Checking ngrok status..."
    override val saveToken = "Save Token"
    override val saving = "Saving..."

    // ── Client Dialog ──
    override val connectToHost = "Connect to Host"
    override val address = "Address"
    override val ngrokUrl = "ngrok URL"
    override val addressPlaceholder = "localhost or xxxx.ngrok-free.app"
    override val ngrokDetected = "ngrok detected - port will be set automatically"
    override val hostCode = "Host Code"

    // ── Tabs ──
    override val tabMyNode = "My Node"
    override fun tabNodes(count: Int) = "Nodes ($count)"
    override val tabSettings = "Settings"

    // ── Host Dashboard ──
    override val host = "Host"
    override val client = "Client"
    override fun runningOf(running: Int, total: Int) = "$running of $total running"
    override fun nodesCount(count: Int) = "$count nodes"
    override val codeCopied = "COPIED"
    override val codeLabel = "CODE"

    // ── Container Card ──
    override val containerRunning = "Running"
    override val containerExited = "Exited"
    override val containerPaused = "Paused"
    override val containerDead = "Dead"
    override val containerCreated = "Created"

    // ── Node Card ──
    override val noContainers = "No containers"
    override val fullControl = "Full Control"
    override val readOnly = "Read Only"
    override val denied = "Denied"

    // ── Deploy Dialog ──
    override val deployContainer = "Deploy Container"
    override val sourceContainer = "Source Container"
    override val deployMode = "Deploy Mode"
    override val instant = "Instant"
    override val approval = "Approval"
    override val instantDesc = "Deploys immediately without client consent"
    override val approvalDesc = "Client must accept before deployment starts"
    override val targetNodes = "Target Nodes"
    override val noRemoteNodes = "No remote nodes connected"
    override val options = "Options"
    override val containerName = "Container Name"
    override val environmentLabel = "Environment (KEY=VALUE, one per line)"
    override val volumesLabel = "Volumes (host:container, one per line)"
    override val deploy = "Deploy"

    // ── Group Deploy Dialog ──
    override val deployGroup = "Deploy Group"
    override val project = "Project"
    override fun containersCount(count: Int) = "$count container(s)"
    override fun deployAll(count: Int) = "Deploy All ($count)"

    // ── Deploy Notification Dialog ──
    override val deployRequest = "Deploy Request"
    override fun deployRequestMessage(hostName: String) = "$hostName wants to deploy a container to this node."
    override val later = "Later"
    override val accept = "Accept"
    override val image = "Image"
    override val name = "Name"
    override val ports = "Ports"
    override val env = "Env"
    override val volumes = "Volumes"

    // ── Pending Deploys ──
    override fun pendingDeploys(count: Int) = "Pending Deploys ($count)"
    override fun deployRequestFrom(hostName: String) = "Deploy request from $hostName"

    // ── Container Group Header ──
    override val group = "\u2197 Group"

    // ── Log Panel ──
    override fun logsFor(name: String) = "Logs: $name"

    // ── Update Banner ──
    override fun updateAvailable(version: String) = "Update available: v$version"

    // ── Settings ──
    override val displayName = "Display Name"
    override val displayNameDesc = "Shown to other nodes when you connect"
    override val nameLabel = "Name"
    override val namePlaceholder = "e.g. John's MacBook"
    override val launchAtStartup = "Launch at startup"
    override val launchAtStartupDesc = "Start DRO when your computer boots"
    override val version = "Version"
    override fun currentVersion(v: String) = "Current: v$v"
    override fun newVersionAvailable(v: String) = "New version available: v$v"
    override val upToDate = "You're up to date!"
    override val checkForUpdates = "Check for Updates"
    override val checking = "Checking..."
    override val language = "Language"
    override val languageDesc = "Select display language"

    // ── Settings (Host) ──
    override val general = "General"
    override val defaultPermission = "Default permission for new nodes"
    override val defaultPermissionDesc = "Applied when a new client joins"
    override val wsPingInterval = "WebSocket ping interval"
    override val wsPingIntervalDesc = "How often to check connection health"

    // ── Menu ──
    override val menuSettings = "Settings"
    override val menuPreferences = "Preferences..."

    // ── Status Messages (ViewModel) ──
    override val startingServer = "Starting server..."
    override fun serverRunningOnPort(port: Int) = "Server running on port $port"
    override val serverStopped = "Server stopped"
    override val dockerNotAvailable = "Docker not available"
    override val commandSent = "Command sent"
    override val commandSendFailed = "Failed to send command"
    override fun connectingTo(host: String, port: Int) = "Connecting to $host:$port..."
    override val connectionFailed = "Connection failed"
    override val connectionRejected = "Connection rejected: Invalid host code"
    override val connectedToServer = "Connected to server"
    override val disconnected = "Disconnected"
    override fun instantDeploySent(image: String) = "Instant deploy sent: $image"
    override fun approvalDeploySent(image: String) = "Approval deploy sent: $image"
    override fun deployToNodeFailed(nodeId: String) = "Failed to send deploy to node $nodeId"
    override val deploySuccessful = "Deploy successful"
    override fun deployFailed(msg: String) = "Deploy failed: $msg"
    override fun deployingLocally(image: String) = "Deploying $image locally..."
    override val dockerNotAvailableForDeploy = "Docker not available for local deploy"
    override fun deployPhaseStatus(phase: String, msg: String) = "[Deploy] $phase: $msg"
}

// ── Korean Implementation ──

object KoStrings : Strings {
    // ── Common ──
    override val appTitle = "Docker Remote\nOrchestrator"
    override val appSubtitle = "네트워크 전반의 컨테이너를 관리하세요"
    override val cancel = "취소"
    override val save = "저장"
    override val close = "닫기"
    override val open = "열기"
    override val start = "시작"
    override val stop = "중지"
    override val connect = "연결"
    override val disconnect = "연결 해제"
    override val done = "완료"
    override val settings = "설정"
    override val download = "다운로드"
    override val dismiss = "닫기"

    // ── Home Screen ──
    override val startAsHost = "호스트로 시작"
    override val joinAsClient = "클라이언트로 참가"
    override val hostServerRunning = "호스트 서버 실행 중"
    override val connectedAsClient = "클라이언트로 연결됨"
    override fun hostCodeNodes(code: String, count: Int) = "코드: $code  ·  노드 ${count}개"
    override fun statusLabel(status: String) = "상태: $status"

    // ── Host Dialog ──
    override val startHost = "호스트 시작"
    override val port = "포트"
    override val externalAccess = "외부 접속 (ngrok)"
    override val allowExternalConnections = "네트워크 외부에서의 연결을 허용합니다"
    override val ngrokNotInstalled = "ngrok이 설치되지 않음"
    override val ngrokNotConfigured = "ngrok 인증 토큰이 설정되지 않음"
    override val checkingNgrok = "ngrok 확인 중..."
    override val setup = "설정"

    // ── ngrok Setup Dialog ──
    override val ngrokSetup = "ngrok 설정"
    override val ngrokNotInstalledDesc = "ngrok이 설치되어 있지 않습니다."
    override val installSteps = "설치 방법:"
    override val orDownloadFrom = "또는 https://ngrok.com/download 에서 다운로드하세요"
    override val afterInstallingReopen = "설치 후 이 대화창을 다시 여세요."
    override val ngrokNeedsToken = "ngrok이 설치되어 있지만 인증 토큰이 필요합니다."
    override val ngrokTokenInstructions = "1. https://ngrok.com 에서 가입하세요\n2. 대시보드에서 인증 토큰을 복사하세요\n3. 아래에 붙여넣으세요:"
    override val authToken = "인증 토큰"
    override val tokenSaved = "토큰이 성공적으로 저장되었습니다!"
    override val tokenSaveFailed = "토큰 저장에 실패했습니다."
    override val ngrokReady = "ngrok을 사용할 준비가 되었습니다!"
    override val checkingNgrokStatus = "ngrok 상태 확인 중..."
    override val saveToken = "토큰 저장"
    override val saving = "저장 중..."

    // ── Client Dialog ──
    override val connectToHost = "호스트에 연결"
    override val address = "주소"
    override val ngrokUrl = "ngrok URL"
    override val addressPlaceholder = "localhost 또는 xxxx.ngrok-free.app"
    override val ngrokDetected = "ngrok이 감지되었습니다 - 포트가 자동으로 설정됩니다"
    override val hostCode = "호스트 코드"

    // ── Tabs ──
    override val tabMyNode = "내 노드"
    override fun tabNodes(count: Int) = "노드 ($count)"
    override val tabSettings = "설정"

    // ── Host Dashboard ──
    override val host = "호스트"
    override val client = "클라이언트"
    override fun runningOf(running: Int, total: Int) = "${total}개 중 ${running}개 실행 중"
    override fun nodesCount(count: Int) = "${count}개 노드"
    override val codeCopied = "복사됨"
    override val codeLabel = "코드"

    // ── Container Card ──
    override val containerRunning = "실행 중"
    override val containerExited = "종료"
    override val containerPaused = "일시정지"
    override val containerDead = "종료됨"
    override val containerCreated = "생성됨"

    // ── Node Card ──
    override val noContainers = "컨테이너 없음"
    override val fullControl = "전체 제어"
    override val readOnly = "읽기 전용"
    override val denied = "거부됨"

    // ── Deploy Dialog ──
    override val deployContainer = "컨테이너 배포"
    override val sourceContainer = "소스 컨테이너"
    override val deployMode = "배포 모드"
    override val instant = "즉시"
    override val approval = "승인"
    override val instantDesc = "클라이언트 동의 없이 즉시 배포합니다"
    override val approvalDesc = "배포 전에 클라이언트가 수락해야 합니다"
    override val targetNodes = "대상 노드"
    override val noRemoteNodes = "연결된 원격 노드 없음"
    override val options = "옵션"
    override val containerName = "컨테이너 이름"
    override val environmentLabel = "환경 변수 (KEY=VALUE, 한 줄에 하나씩)"
    override val volumesLabel = "볼륨 (host:container, 한 줄에 하나씩)"
    override val deploy = "배포"

    // ── Group Deploy Dialog ──
    override val deployGroup = "그룹 배포"
    override val project = "프로젝트"
    override fun containersCount(count: Int) = "컨테이너 ${count}개"
    override fun deployAll(count: Int) = "전체 배포 (${count}개)"

    // ── Deploy Notification Dialog ──
    override val deployRequest = "배포 요청"
    override fun deployRequestMessage(hostName: String) = "$hostName 이(가) 이 노드에 컨테이너를 배포하려 합니다."
    override val later = "나중에"
    override val accept = "수락"
    override val image = "이미지"
    override val name = "이름"
    override val ports = "포트"
    override val env = "환경 변수"
    override val volumes = "볼륨"

    // ── Pending Deploys ──
    override fun pendingDeploys(count: Int) = "대기 중인 배포 ($count)"
    override fun deployRequestFrom(hostName: String) = "$hostName 의 배포 요청"

    // ── Container Group Header ──
    override val group = "\u2197 그룹"

    // ── Log Panel ──
    override fun logsFor(name: String) = "로그: $name"

    // ── Update Banner ──
    override fun updateAvailable(version: String) = "업데이트 가능: v$version"

    // ── Settings ──
    override val displayName = "표시 이름"
    override val displayNameDesc = "다른 노드에 표시되는 이름입니다"
    override val nameLabel = "이름"
    override val namePlaceholder = "예: 홍길동의 MacBook"
    override val launchAtStartup = "시작 시 자동 실행"
    override val launchAtStartupDesc = "컴퓨터 부팅 시 DRO를 자동으로 시작합니다"
    override val version = "버전"
    override fun currentVersion(v: String) = "현재: v$v"
    override fun newVersionAvailable(v: String) = "새 버전 사용 가능: v$v"
    override val upToDate = "최신 버전입니다!"
    override val checkForUpdates = "업데이트 확인"
    override val checking = "확인 중..."
    override val language = "언어"
    override val languageDesc = "표시 언어를 선택하세요"

    // ── Settings (Host) ──
    override val general = "일반"
    override val defaultPermission = "새 노드의 기본 권한"
    override val defaultPermissionDesc = "새 클라이언트가 접속할 때 적용됩니다"
    override val wsPingInterval = "WebSocket 핑 간격"
    override val wsPingIntervalDesc = "연결 상태를 확인하는 주기"

    // ── Menu ──
    override val menuSettings = "설정"
    override val menuPreferences = "환경설정..."

    // ── Status Messages (ViewModel) ──
    override val startingServer = "서버 시작 중..."
    override fun serverRunningOnPort(port: Int) = "포트 $port 에서 서버 실행 중"
    override val serverStopped = "서버가 중지되었습니다"
    override val dockerNotAvailable = "Docker를 사용할 수 없습니다"
    override val commandSent = "명령이 전송되었습니다"
    override val commandSendFailed = "명령 전송에 실패했습니다"
    override fun connectingTo(host: String, port: Int) = "$host:$port 에 연결 중..."
    override val connectionFailed = "연결에 실패했습니다"
    override val connectionRejected = "연결이 거부되었습니다: 잘못된 호스트 코드"
    override val connectedToServer = "서버에 연결되었습니다"
    override val disconnected = "연결이 해제되었습니다"
    override fun instantDeploySent(image: String) = "즉시 배포 전송됨: $image"
    override fun approvalDeploySent(image: String) = "승인 배포 전송됨: $image"
    override fun deployToNodeFailed(nodeId: String) = "노드 $nodeId 에 배포 전송 실패"
    override val deploySuccessful = "배포가 완료되었습니다"
    override fun deployFailed(msg: String) = "배포 실패: $msg"
    override fun deployingLocally(image: String) = "$image 로컬 배포 중..."
    override val dockerNotAvailableForDeploy = "로컬 배포를 위한 Docker를 사용할 수 없습니다"
    override fun deployPhaseStatus(phase: String, msg: String) = "[배포] $phase: $msg"
}
