import { getToken } from '../stores/auth'

export async function streamDiagnose(payload, handlers) {
  const token = getToken()
  const response = await fetch('/api/diagnose/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok || !response.body) {
    let msg = `请求失败（${response.status}）`
    try {
      const body = await response.json()
      msg = body.message || body.error || msg
    } catch (e) {
      // 非 JSON 错误体，保留默认消息
    }
    throw new Error(msg)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (raw) => {
    const event = parseSseEvent(raw)
    if (event.event === 'conversation' && handlers.onConversation) {
      handlers.onConversation(event.data)
    } else if (event.event === 'text' && handlers.onDelta) {
      handlers.onDelta(event.data)
    } else if (event.event === 'tool' && handlers.onTool) {
      handlers.onTool(event.data)
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sep
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const raw = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      if (raw.trim()) dispatch(raw)
    }
  }
  buffer += decoder.decode()
  if (buffer.trim()) dispatch(buffer)
}

function parseSseEvent(raw) {
  let event = 'message'
  const dataLines = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  return { event, data: dataLines.join('\n') }
}
