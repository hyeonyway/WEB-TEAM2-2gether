import {describe,expect,it} from 'vitest';
import {decodeAccessTokenUserId} from './jwtClaims';

function tokenWithPayload(payload:unknown):string{
  return `header.${btoa(JSON.stringify(payload))}.signature`;
}

describe('decodeAccessTokenUserId',()=>{
  it('sub 클레임을 숫자 userId로 디코드한다',()=>{
    expect(decodeAccessTokenUserId(tokenWithPayload({sub:'42'}))).toBe(42);
  });

  it('sub가 없으면 null을 반환한다',()=>{
    expect(decodeAccessTokenUserId(tokenWithPayload({}))).toBeNull();
  });

  it('sub가 숫자가 아니면 null을 반환한다',()=>{
    expect(decodeAccessTokenUserId(tokenWithPayload({sub:'abc'}))).toBeNull();
  });

  it('sub가 0 이하면 null을 반환한다',()=>{
    expect(decodeAccessTokenUserId(tokenWithPayload({sub:'0'}))).toBeNull();
  });

  it('payload 세그먼트가 없으면 null을 반환한다',()=>{
    expect(decodeAccessTokenUserId('not-a-jwt')).toBeNull();
  });

  it('payload가 유효한 base64/JSON이 아니면 null을 반환한다',()=>{
    expect(decodeAccessTokenUserId('header.not-base64!!.signature')).toBeNull();
  });
});
