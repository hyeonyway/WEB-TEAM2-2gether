import {beforeEach, describe, expect, it, vi} from 'vitest';

import {HttpError, request} from './httpClient';

describe('request', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  it('공통 JSON 오류 응답의 code와 message를 분리한다', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(
      JSON.stringify({code: 'CARD_NOT_FOUND', message: '카드를 찾을 수 없습니다.'}),
      {status: 404, headers: {'Content-Type': 'application/json'}},
    ));

    await expect(request('/api/cards/1')).rejects.toMatchObject({
      status: 404,
      code: 'CARD_NOT_FOUND',
      message: '카드를 찾을 수 없습니다.',
    } satisfies Partial<HttpError>);
  });

  it('JSON이 아니거나 message가 없으면 상태 기반 fallback 메시지를 사용한다', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response('<html>error</html>', {status: 500}));
    await expect(request('/api/cards/1')).rejects.toMatchObject({
      status: 500,
      message: '요청 처리 중 오류가 발생했습니다.',
    } satisfies Partial<HttpError>);

    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({code: 'UNKNOWN'}), {status: 400}));
    await expect(request('/api/cards/1')).rejects.toMatchObject({
      status: 400,
      code: 'UNKNOWN',
      message: '요청 정보를 확인해 주세요.',
    } satisfies Partial<HttpError>);
  });
});
