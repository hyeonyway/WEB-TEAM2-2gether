import {render, screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import ToastContainer, {showToast} from './Toast';

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
});
