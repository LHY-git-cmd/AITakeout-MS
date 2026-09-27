/** 验证Agent SSE跨分片解析、事件序号和结构化数据保持不变。 */
import { describe, expect, it } from 'vitest'
import { parseAgentSse } from '../src/api/agent.ts'

function byteStream(parts: string[]) {
  const encoder = new TextEncoder()
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const part of parts) controller.enqueue(encoder.encode(part))
      controller.close()
    },
  })
}

describe('Agent SSE解析器', () => {
  it('正确处理跨网络分片的JSON和CRLF边界', async () => {
    const stream = byteStream([
      'id: 1\r\nevent: message_delta\r\ndata: {"task_id":"t1","seq_no":1,',
      '"event":"message_delta","data":{"content":"你',
      '好"}}\r\n\r\nid: 2\nevent: task_completed\ndata: {"task_id":"t1","seq_no":2,"event":"task_completed","data":{"status":"completed"}}\n\n',
    ])

    const events = []
    for await (const event of parseAgentSse(stream)) events.push(event)

    expect(events.map((event) => event.event)).toEqual(['message_delta', 'task_completed'])
    expect(events[0]?.data).toEqual({ content: '你好' })
    expect(events[1]?.seq_no).toBe(2)
  })
})
