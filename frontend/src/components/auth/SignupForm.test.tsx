import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it, vi} from 'vitest';
import SignupForm from './SignupForm';

function renderSignupForm() {
  const queryClient = new QueryClient({
    defaultOptions: {queries: {retry: false}, mutations: {retry: false}},
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <SignupForm onSuccess={vi.fn()}/>
    </QueryClientProvider>,
  );
}

describe('SignupForm', () => {
  it('로마 숫자와 특수문자만으로 구성된 비밀번호를 거부한다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'collector@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'ⅧⅧⅧⅧⅧⅧⅧⅧ!!');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'ⅧⅧⅧⅧⅧⅧⅧⅧ!!');
    await user.type(screen.getByLabelText('닉네임'), '포켓수집가');
    await user.click(screen.getByRole('button', {name: '회원가입'}));

    expect(screen.getByRole('alert')).toHaveTextContent(
      '비밀번호는 3종 조합 8자 이상 또는 2종 조합 10자 이상이어야 합니다.',
    );
  });

  it('입력 필드에 초점을 맞추면 해당 규칙 안내를 표시한다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.click(screen.getByLabelText('닉네임'));

    expect(screen.getByText('2~30자, 한글·영문·숫자만 사용')).toBeInTheDocument();
  });
});
