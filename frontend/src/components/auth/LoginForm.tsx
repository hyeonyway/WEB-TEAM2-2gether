import {useMutation} from '@tanstack/react-query';
import {type FormEvent, useState} from 'react';
import {HttpError} from '../../api/httpClient';
import {authMutations} from '../../queries/authMutations';

type LoginFormProps = {
  initialEmail?: string;
  onSuccess: () => void;
};

type LoginErrors = Partial<Record<'email' | 'password' | 'submit', string>>;

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function LoginForm({initialEmail = '', onSuccess}: LoginFormProps) {
  const [values, setValues] = useState({
    email: initialEmail,
    password: '',
  });
  const [errors, setErrors] = useState<LoginErrors>({});
  const loginMutation = useMutation(authMutations.login());

  const updateValue = (name: keyof typeof values, value: string) => {
    setValues(current => ({...current, [name]: value}));
    setErrors(current => ({...current, [name]: undefined, submit: undefined}));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (loginMutation.isPending) return;

    const validationErrors: LoginErrors = {};
    if (!emailPattern.test(values.email) || values.email.length > 255) {
      validationErrors.email = '올바른 이메일 주소를 입력해 주세요.';
    }
    if (values.password.length === 0) {
      validationErrors.password = '비밀번호를 입력해 주세요.';
    } else if (values.password.length > 128) {
      validationErrors.password = '비밀번호는 128자 이하로 입력해 주세요.';
    }
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    loginMutation.mutate(values, {
      onSuccess,
      onError: error => {
        setErrors({
          submit: error instanceof HttpError && error.status === 401
            ? '이메일 또는 비밀번호가 일치하지 않습니다.'
            : '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.',
        });
      },
    });
  };

  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit}>
      <label>
        이메일
        <input
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={event => updateValue('email', event.target.value)}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'login-email-error' : undefined}
        />
        {errors.email && (
          <small id="login-email-error" className="auth-field-error" role="alert">
            {errors.email}
          </small>
        )}
      </label>
      <label>
        비밀번호
        <input
          type="password"
          autoComplete="current-password"
          value={values.password}
          onChange={event => updateValue('password', event.target.value)}
          aria-invalid={Boolean(errors.password)}
          aria-describedby={errors.password ? 'login-password-error' : undefined}
        />
        {errors.password && (
          <small id="login-password-error" className="auth-field-error" role="alert">
            {errors.password}
          </small>
        )}
      </label>
      {errors.submit && <p className="auth-submit-error" role="alert">{errors.submit}</p>}
      <button className="auth-primary-button" type="submit" disabled={loginMutation.isPending}>
        {loginMutation.isPending ? '로그인 중...' : '로그인'}
      </button>
    </form>
  );
}
