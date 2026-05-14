interface WebSocketHandlers {
  onMessage?: (data: any) => void
  onOpen?: () => void
  onClose?: () => void
  onError?: (error: Event) => void
}

class AIWebSocketManager {
  private ws: WebSocket | null = null
  private url: string = ''
  private handlers: WebSocketHandlers = {}
  private reconnectTimer: number | null = null
  private maxReconnectAttempts = 5
  private reconnectAttempts = 0

  constructor() {
    const token = localStorage.getItem('token')
    this.url = `ws://${window.location.host}/pet-system/ai/ws?token=${token}`
  }

  connect(handlers: WebSocketHandlers) {
    this.handlers = handlers

    try {
      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        console.log('AI WebSocket 连接成功')
        this.reconnectAttempts = 0
        this.handlers.onOpen?.()
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handlers.onMessage?.(data)
        } catch (error) {
          console.error('解析消息失败:', error)
        }
      }

      this.ws.onclose = () => {
        console.log('AI WebSocket 连接关闭')
        this.handlers.onClose?.()
        this.handleReconnect()
      }

      this.ws.onerror = (error) => {
        console.error('AI WebSocket 错误:', error)
        this.handlers.onError?.(error)
      }
    } catch (error) {
      console.error('创建 WebSocket 连接失败:', error)
      this.handleReconnect()
    }
  }

  sendMessage(message: string) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ message }))
    } else {
      console.error('WebSocket 未连接')
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private handleReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      this.reconnectTimer = window.setTimeout(() => {
        console.log(`尝试重新连接 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
        this.connect(this.handlers)
      }, 3000)
    }
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }
}

export const aiWebSocket = new AIWebSocketManager()
