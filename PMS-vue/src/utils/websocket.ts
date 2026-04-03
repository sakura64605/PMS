import SockJS from 'sockjs-client'

// 为SockJS添加类型定义
declare module 'sockjs-client' {
  class SockJS {
    constructor(url: string, protocols?: string | string[], options?: any)
    onopen: ((event: any) => void) | null
    onmessage: ((event: any) => void) | null
    onclose: ((event: any) => void) | null
    onerror: ((event: any) => void) | null
    readyState: number
    close(): void
  }
  export = SockJS
}

class WebSocketService {
  private socket: any = null
  private messageCallback: ((message: any) => void) | null = null
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 2000

  // 初始化WebSocket连接
  init(token: string) {
    if (this.socket) {
      this.socket.close()
    }

    this.socket = new SockJS(`/pet-system/ws?token=${token}`)
    this.socket.onopen = () => {
      console.log('WebSocket连接成功')
      this.reconnectAttempts = 0
    }

    this.socket.onmessage = (event: any) => {
      try {
        const message = JSON.parse(event.data)
        if (this.messageCallback) {
          this.messageCallback(message)
        }
      } catch (error) {
        console.error('WebSocket消息解析失败:', error)
      }
    }

    this.socket.onclose = () => {
      console.log('WebSocket连接关闭')
      this.attemptReconnect(token)
    }

    this.socket.onerror = (error: any) => {
      console.error('WebSocket错误:', error)
    }
  }

  // 尝试重连
  private attemptReconnect(token: string) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`WebSocket重连中... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
      setTimeout(() => {
        this.init(token)
      }, this.reconnectDelay)
    } else {
      console.error('WebSocket重连失败，已达到最大尝试次数')
    }
  }

  // 设置消息回调
  setMessageCallback(callback: (message: any) => void) {
    this.messageCallback = callback
  }

  // 关闭连接
  close() {
    if (this.socket) {
      this.socket.close()
      this.socket = null
    }
  }

  // 检查连接状态
  isConnected() {
    return this.socket && this.socket.readyState === 1
  }
}

// 导出单例
export default new WebSocketService()
