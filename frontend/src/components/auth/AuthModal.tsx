import {useEffect, useRef, useState} from 'react';
import LoginForm from './LoginForm';
import SignupForm from './SignupForm';
import './AuthModal.css';

type AuthMode = 'login' | 'signup';

type AuthModalProps = {
  open: boolean;
  onClose: () => void;
  onLoginSuccess?: () => void;
};

export default function AuthModal({open, onClose, onLoginSuccess = onClose}: AuthModalProps) {
  const [mode, setMode] = useState<AuthMode>('login');
  const [loginEmail, setLoginEmail] = useState('');
  const [notice, setNotice] = useState('');
  const dialogRef = useRef<HTMLElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      const dialog = dialogRef.current;
      if (!dialog) return;
      const focusableElements = Array.from(dialog.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), '
        + 'select:not([disabled]), textarea:not([disabled]), '
        + '[tabindex]:not([tabindex="-1"])',
      ));
      const first = focusableElements[0];
      const last = focusableElements.at(-1);
      if (!first || !last) {
        event.preventDefault();
        dialog.focus();
        return;
      }

      if (event.shiftKey && (document.activeElement === first
        || !dialog.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last
        || !dialog.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  useEffect(() => {
    if (open) {
      setMode('login');
      setNotice('');
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;

    previousFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;

    return () => {
      previousFocusRef.current?.focus();
      previousFocusRef.current = null;
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;

    const dialog = dialogRef.current;
    const initialFocus = dialog?.querySelector<HTMLElement>('input:not([disabled])');
    (initialFocus ?? dialog)?.focus();
  }, [open, mode]);

  if (!open) return null;

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setNotice('');
  };

  const handleSignupSuccess = (email: string) => {
    setLoginEmail(email);
    setNotice('가입이 완료되었습니다. 로그인해 주세요.');
    setMode('login');
  };

  return (
    <div
      className="auth-modal-backdrop"
      data-testid="auth-modal-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        ref={dialogRef}
        className="auth-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-modal-title"
        tabIndex={-1}
      >
        <button
          type="button"
          className="auth-modal-close"
          aria-label="인증 모달 닫기"
          onClick={onClose}
        >
          ×
        </button>
        <div className="auth-modal-brand">
          <span>DIBIDDING</span>
          <strong>좋아하는 카드를<br/>더 가까이.</strong>
          <p>안전한 국내 거래와 실시간 경매를 한곳에서 만나보세요.</p>
        </div>
        <div className="auth-modal-content">
          <small className="auth-eyebrow">WELCOME TO DIBIDDING</small>
          <h2 id="auth-modal-title">{mode === 'login' ? '계정 로그인' : '회원가입'}</h2>
          <p className="auth-modal-description">
            {mode === 'login'
              ? '경매 현황과 내 컬렉션을 이어서 확인하세요.'
              : '간단한 정보만 입력하면 바로 시작할 수 있어요.'}
          </p>
          {notice && <p className="auth-success-notice">{notice}</p>}
          {mode === 'login'
            ? <LoginForm initialEmail={loginEmail} onSuccess={onLoginSuccess}/>
            : <SignupForm onSuccess={handleSignupSuccess}/>}
          <div className="auth-mode-switch">
            <span>{mode === 'login' ? '아직 계정이 없으신가요?' : '이미 계정이 있으신가요?'}</span>
            <button
              type="button"
              onClick={() => switchMode(mode === 'login' ? 'signup' : 'login')}
            >
              {mode === 'login' ? '회원가입하기' : '로그인하기'}
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
