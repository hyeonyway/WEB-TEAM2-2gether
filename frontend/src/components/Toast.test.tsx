import {render, screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import ToastContainer, {showToast} from './Toast';
import '../tailwind.css';

describe('ToastContainer', () => {
  afterEach(() => {
    vi.runOnlyPendingTimers();
    vi.useRealTimers();
  });

  it('구독 전에 발생한 토스트도 첫 렌더에서 표시한다', () => {
    vi.useFakeTimers();

    showToast('로그인이 필요합니다');
    render(<ToastContainer/>);

    expect(screen.getByText('로그인이 필요합니다')).toBeInTheDocument();
  });

  it('같은 중복 키의 활성 토스트는 한 번만 표시한다', () => {
    vi.useFakeTimers();

    showToast('로그인이 필요합니다', 'auth-required');
    showToast('로그인이 필요합니다', 'auth-required');
    render(<ToastContainer/>);

    expect(screen.getAllByText('로그인이 필요합니다')).toHaveLength(1);
  });

  it('토스트를 헤더 아래 가로 중앙에 표시한다', () => {
    vi.useFakeTimers();
    showToast('로그인이 필요합니다');
    render(<ToastContainer/>);

    const stack = document.body.querySelector('.toast-stack');
    expect(stack).not.toBeNull();
    expect(stack?.parentElement).toBe(document.body);
    expect(getComputedStyle(stack!).top).toBe('81px');
    expect(getComputedStyle(stack!).left).toBe('50%');
    expect(getComputedStyle(stack!).transform).toBe('translateX(-50%)');
    expect(getComputedStyle(stack!).bottom).toBe('auto');
  });
});
