import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { NotificationResponse } from './types'

export function connectNotificationSocket(
  token: string,
  onNotification: (notification: NotificationResponse) => void,
): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/user/queue/notifications', (message: IMessage) => {
        try {
          onNotification(JSON.parse(message.body) as NotificationResponse)
        } catch {
          // ignore malformed payloads
        }
      })
    },
  })

  client.activate()
  return client
}
