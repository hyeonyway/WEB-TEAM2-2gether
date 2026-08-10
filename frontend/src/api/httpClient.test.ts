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

  it.each([
    [400, '', '요청 정보를 확인해 주세요.'],
    [401, '<html>login</html>', '로그인이 필요하거나 로그인 정보가 만료되었습니다.'],
    [403, '{', '접근 권한이 없습니다.'],
    [404, JSON.stringify({code: 'MISSING_MESSAGE'}), '요청한 정보를 찾을 수 없습니다.'],
    [500, '<html>error</html>', '요청 처리 중 오류가 발생했습니다.'],
  ])('상태 %i와 유효하지 않은 오류 본문에는 fallback 메시지를 사용한다', async(status,body,message) => {
    vi.mocked(fetch).mockResolvedValue(new Response(body, {status}));
    await expect(request('/api/cards/1')).rejects.toMatchObject({status,message});
  });

  it('code 또는 message가 누락되면 서버 오류 값을 사용하지 않는다', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response('<html>error</html>', {status: 500}));
    await expect(request('/api/cards/1')).rejects.toMatchObject({
      status: 500,
      message: '요청 처리 중 오류가 발생했습니다.',
    } satisfies Partial<HttpError>);

    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({code: 'UNKNOWN'}), {status: 400}));
    await expect(request('/api/cards/1')).rejects.toMatchObject({
      status: 400,
      message: '요청 정보를 확인해 주세요.',
    } satisfies Partial<HttpError>);
  });
});
