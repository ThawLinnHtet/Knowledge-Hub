import { afterEach, describe, expect, it, vi } from 'vitest'

import { streamChat, type StreamEvent } from './chat-api'

function streamedResponse(chunks: string[]) {
  const encoder = new TextEncoder()
  return {
    ok: true,
    status: 200,
    body: new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
        controller.close()
      },
    }),
    json: async () => ({}),
  }
}

describe('chat SSE client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('parses fragmented CRLF frames and stops at completion', async () => {
    const events: StreamEvent[] = []
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          streamedResponse([
            'event:delta\r',
            '\ndata:{"messageId":"m1","text":"Grounded "}\r\n\r',
            '\nevent:completed\r\ndata:{"messageId":"m1","content":"Grounded answer","evidenceStatus":"SUPPORTED"}\r\n\r\n',
          ]),
        ),
    )

    await streamChat(
      'chat-1',
      'Question',
      { type: 'ALL', collectionId: null, documentIds: [] },
      'token',
      (event) => events.push(event),
    )

    expect(events.map((event) => event.event)).toEqual(['delta', 'completed'])
  })

  it('rejects a stream that ends without a terminal event', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          streamedResponse([
            'event:delta\ndata:{"messageId":"m1","text":"Partial"}\n\n',
          ]),
        ),
    )

    await expect(
      streamChat(
        'chat-1',
        'Question',
        { type: 'ALL', collectionId: null, documentIds: [] },
        'token',
        () => undefined,
      ),
    ).rejects.toThrow('interrupted before completion')
  })
})
