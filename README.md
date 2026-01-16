# Media Control por Gestos

Aplicativo Android desenvolvido em Kotlin com Jetpack Compose que permite controlar a reprodução de mídia (música, podcasts, etc.) através de gestos de inclinação do dispositivo, eliminando a necessidade de interação com a tela.

## Visão Geral
- Controle de mídia hands-free usando sensores de acelerômetro e giroscópio do Android.
- Detecta inclinação lateral do dispositivo para pular músicas (direita = próxima, esquerda = anterior).
- Motor de gestos com máquina de estados que garante detecção precisa e evita comandos acidentais.
- Serviço em foreground que mantém o monitoramento de sensores mesmo com a tela desligada.

## Funcionalidades
- **Controle por Gestos**: Incline o dispositivo para a direita/esquerda para avançar/voltar músicas.
- **Detecção Inteligente**: Algoritmo de duplo estágio (inclinação → retorno ao neutro) previne comandos não intencionais.
- **Threshold Configurável**: Ângulo de inclinação ajustável (padrão: 30°) para personalizar sensibilidade.
- **Monitoramento em Background**: Serviço foreground com notificação persistente mantém funcionalidade ativa.
- **Compatibilidade Universal**: Funciona com qualquer app de áudio que implemente `MediaSession` (Spotify, YouTube Music, etc.).

## Demonstração em Vídeo
> ⚠️ **TODO**: Adicionar vídeo demonstrando o controle por gestos em `previews/Preview_APP_Video.mp4`

## Pré-visualizações
> ⚠️ **TODO**: Adicionar screenshots:
> - `previews/Preview_APP_Home_Light.png` - Tela inicial modo claro
> - `previews/Preview_APP_Home_Dark.png` - Tela inicial modo escuro
> - `previews/Preview_APP_Sensor_Data.png` - Dados dos sensores em tempo real
> - `previews/Preview_APP_Notification.png` - Notificação do serviço foreground

## Arquitetura do Sistema de Gestos

### 1. Motor de Detecção (`GestureEngine`)

O coração do app é uma **máquina de estados** que processa dados brutos dos sensores e os converte em comandos de mídia:

```kotlin
class GestureEngine(val config: Config = Config()) {
    data class Config(
        val tiltThresholdDeg: Float = 30f,
        val neutralThresholdDeg: Float = 5f,
        val debounceMs: Long = 800
    )
    
    enum class Command { SKIP_NEXT, SKIP_PREVIOUS }
    
    private enum class TiltState { IDLE, TILTED }
    private var tiltState = TiltState.IDLE
    private var lastCommandTime = 0L
    
    fun onRoll(rollDeg: Float): Command? {
        val side = classifySide(rollDeg)
        return when (tiltState) {
            TiltState.IDLE -> {
                if (side != Side.NEUTRAL) {
                    tiltingSide = side
                    tiltState = TiltState.TILTED
                }
                null
            }
            TiltState.TILTED -> {
                if (side == Side.NEUTRAL && canEmitCommand()) {
                    val cmd = tiltingSide.toCommand()
                    reset()
                    cmd
                } else null
            }
        }
    }
}
```

**Fluxo de Detecção:**
1. **IDLE**: Aguarda inclinação além do threshold (30°)
2. **TILTED**: Registra lado inclinado e espera retorno ao neutro (< 5°)
3. **Comando**: Emite `SKIP_NEXT`/`SKIP_PREVIOUS` e entra em debounce (800ms)

**Benefícios da Abordagem:**
- ✅ **Zero falsos positivos**: Requer movimento completo (ida + volta)
- ✅ **Controle fino**: Thresholds separados para ativação e neutralizaç��o
- ✅ **Debounce inteligente**: Previne comandos repetidos acidentais

### 2. Serviço de Sensores (`GestureForegroundService`)

Implementa `SensorEventListener` para capturar dados de acelerômetro e giroscópio em tempo real:

```kotlin
class GestureForegroundService : Service(), SensorEventListener {
    private val gestureEngine = GestureEngine()
    private lateinit var sensorManager: SensorManager
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val (x, y, z) = event.values
                val rollDeg = atan2(y.toDouble(), z.toDouble())
                    .toDegrees().toFloat()
                
                val command = gestureEngine.onRoll(rollDeg)
                command?.let { executeMediaCommand(it) }
                
                broadcastSensorData(rollDeg, x, y, z)
            }
        }
    }
    
    private fun executeMediaCommand(command: Command) {
        when (command) {
            Command.SKIP_NEXT -> MediaCommander.skipNext()
            Command.SKIP_PREVIOUS -> MediaCommander.skipPrevious()
        }
    }
}
```

**Cálculo do Ângulo de Inclinação:**
- Usa componentes Y e Z do acelerômetro para calcular **roll** (rotação lateral)
- Fórmula: `atan2(accelY, accelZ)` convertida para graus
- Valores positivos = inclinação para direita | Negativos = esquerda

**Registro dos Sensores:**
```kotlin
sensorManager.registerListener(
    this,
    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
    SensorManager.SENSOR_DELAY_GAME // ~20ms latência
)
```

### 3. Controle de Mídia (`MediaCommander` + `MediaNotificationListener`)

Interage com a API `MediaSession` do Android para enviar comandos aos apps de áudio:

```kotlin
object MediaCommander {
    fun skipNext(): Boolean {
        val controller = MediaNotificationListener.getController() 
            ?: return false
        controller.transportControls.skipToNext()
        return true
    }
}

class MediaNotificationListener : NotificationListenerService() {
    companion object {
        private var mediaController: MediaController? = null
        
        fun getController() = mediaController
    }
    
    override fun onListenerConnected() {
        updateController(getActiveSessions(null))
    }
    
    private fun updateController(controllers: List<MediaController>?) {
        mediaController = controllers?.firstOrNull { 
            it.playbackState?.state == PlaybackState.STATE_PLAYING 
        } ?: controllers?.lastOrNull()
    }
}
```

**Funcionamento:**
1. `NotificationListenerService` intercepta sessões de mídia ativas
2. Identifica o player em reprodução (`STATE_PLAYING`)
3. `MediaCommander` envia comandos via `TransportControls`
4. Apps compatíveis (Spotify, YouTube Music, etc.) respondem aos comandos

### 4. Interface Compose (`MainActivity`)

Exibe dados dos sensores em tempo real e gerencia o ciclo de vida do serviço:

```kotlin
@Composable
fun HomeScreen(
    rollDeg: Float,
    accelX: Float, accelY: Float, accelZ: Float,
    side: String,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Column {
        Text("Inclinação: ${rollDeg.format(1)}°")
        Text("Lado: $side")
        Text("Acelerômetro:")
        Text("  X: ${accelX.format(2)} m/s²")
        Text("  Y: ${accelY.format(2)} m/s²")
        Text("  Z: ${accelZ.format(2)} m/s²")
        
        Button(onClick = onStartService) {
            Text("Iniciar Monitoramento")
        }
    }
}
```

**Comunicação Serviço-UI:**
```kotlin
// BroadcastReceiver em MainActivity
val sensorReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        rollDeg = intent.getFloatExtra("roll_deg", 0f)
        side = intent.getStringExtra("side") ?: "Neutro"
        // Atualiza estados que disparam recomposição
    }
}
```

## Permissões Necessárias

O app requer três permissões críticas:

```xml
<!-- AndroidManifest.xml -->

<!-- Serviço em foreground (Android 9+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- Leitura de notificações para acessar MediaSession -->
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    tools:ignore="ProtectedPermissions" />
```

**Fluxo de Permissões:**
1. **FOREGROUND_SERVICE**: Solicitada automaticamente ao iniciar o serviço
2. **NOTIFICATION_LISTENER**: Usuário deve conceder manualmente em Configurações > Notificações
3. App verifica permissões e exibe diálogo de orientaç��o se necessário

## Stack e Ferramentas
- Kotlin 2.0.0
- Jetpack Compose (Material 3 + BOM 2024.04.01)
- Android Gradle Plugin 8.3.2
- Minimum SDK 24 | Target SDK 34
- **Sensores**: Acelerômetro (TYPE_ACCELEROMETER)
- **Mídia**: MediaSession API + NotificationListenerService

## Como Executar

### 1. Pré-requisitos
- Android Studio Hedgehog | Iguana ou superior
- Dispositivo físico com acelerômetro (emuladores têm suporte limitado a sensores)
- JDK 17+

### 2. Instalação
```bash
git clone <repository-url>
cd MediaControl
./gradlew assembleDebug
```

### 3. Configuração de Permissões
Após instalar o APK:
1. Abra **Configurações** > **Notificações** > **Acesso às notificações**
2. Habilite o toggle para **Media Control**
3. Abra o app e inicie o serviço

### 4. Teste dos Gestos
1. Reproduza uma música em qualquer app (Spotify, YouTube Music, etc.)
2. Incline o dispositivo ~45° para a direita → música avança
3. Incline ~45° para a esquerda → música volta
4. Observe os dados dos sensores na tela principal

## Estrutura do Projeto
```
SensorMediaControl/
├── app/src/main/java/com/ufc/mediacontrol/
│   ├── MainActivity.kt                      # UI Compose + Gerenciamento de serviço
│   ├── sensor/
│   │   └── GestureEngine.kt                # Motor de detecção de gestos
│   ├── service/
│   │   ├── GestureForegroundService.kt     # Serviço de sensores
│   │   └── MediaNotificationListener.kt     # Listener de sessões de mídia
│   └── media/
│       └── MediaCommander.kt               # API de comandos de mídia
├── app/src/main/res/
│   ├── values/strings.xml                   # Strings PT-BR
│   └── values-en/strings.xml                # Strings EN
└── previews/                                # ⚠️ TODO: Adicionar screenshots/vídeo
```

## Detalhes Técnicos

### Calibração de Sensores
O acelerômetro retorna valores em m/s² nos três eixos (X, Y, Z). A conversão para ângulo usa:

```kotlin
val rollRad = atan2(accelY, accelZ)
val rollDeg = Math.toDegrees(rollRad)
```

**Interpretação dos Valores:**
- `rollDeg > 30°`: Inclinação para direita (comando: próxima)
- `rollDeg < -30°`: Inclinação para esquerda (comando: anterior)
- `-5° < rollDeg < 5°`: Posição neutra (reset da máquina de estados)

### Otimizações de Performance
- **SENSOR_DELAY_GAME**: Atualização ~20ms, balanceando responsividade e consumo de bateria
- **Debounce de 800ms**: Previne comandos duplicados durante movimento contínuo
- **Broadcast local**: Comunicação serviço-UI usando `Intent` broadcast simples
- **Lazy initialization**: Sensores registrados apenas quando serviço está ativo

### Compatibilidade
Testado e funcional com:
- ✅ Spotify
- ✅ YouTube Music
- ✅ Google Podcasts
- ✅ Players nativos do sistema

**Limitações:**
- ❌ Apps que não implementam `MediaSession` (players web, alguns apps de terceiros)
- ⚠️ Requer permissão de notificação (deve ser solicitada manualmente pelo usuário)