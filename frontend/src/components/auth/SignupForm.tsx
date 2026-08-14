import {useMutation} from '@tanstack/react-query';
import {type FormEvent, useState} from 'react';
import {HttpError} from '../../api/httpClient';
import type {SignupRequestDto} from '../../dto/authDto';
import {authMutations} from '../../queries/authMutations';

type SignupFormProps = {
  onSuccess: (email: string) => void;
};

type SignupErrors = Partial<Record<
  keyof SignupRequestDto | 'passwordConfirmation' | 'submit',
  string
>>;

// Keep in sync with backend SignupRequest.EMAIL_PATTERN and LoginRequest.
const emailPattern = /^[A-Za-z0-9](?:[A-Za-z0-9._%+-]*[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}$/;
const nicknamePattern = /^[가-힣a-zA-Z0-9]+$/;
const passwordKinds = (value: string) => [/[\p{L}]/u.test(value), /[\p{N}]/u.test(value), /[^\p{L}\p{N}]/u.test(value)].filter(Boolean).length;

function validateSignup(
  values: SignupRequestDto & {passwordConfirmation: string},
): SignupErrors {
  const errors: SignupErrors = {};

  if (!emailPattern.test(values.email) || values.email.length > 255) {
    errors.email = '올바른 이메일 주소를 입력해 주세요.';
  }
  const kinds = passwordKinds(values.password);
  if (values.password.length > 128 || kinds < 2 || values.password.length < (kinds >= 3 ? 8 : 10)) {
    errors.password = '비밀번호는 3종 조합 8자 이상 또는 2종 조합 10자 이상이어야 합니다.';
  }
  if (values.password !== values.passwordConfirmation) {
    errors.passwordConfirmation = '비밀번호가 일치하지 않습니다.';
  }
  if (!nicknamePattern.test(values.nickname) || values.nickname.length < 2 || values.nickname.length > 30) {
    errors.nickname = '닉네임은 2~30자 한글, 영문, 숫자만 사용할 수 있습니다.';
  }

  return errors;
}

export default function SignupForm({onSuccess}: SignupFormProps) {
  const [values, setValues] = useState({
    email: '',
    password: '',
    passwordConfirmation: '',
    nickname: '',
  });
  const [errors, setErrors] = useState<SignupErrors>({});
  const [focusedField, setFocusedField] = useState<keyof typeof values | null>(null);
  const signupMutation = useMutation(authMutations.signup());

  const updateValue = (name: keyof typeof values, value: string) => {
    setValues(current => ({...current, [name]: value}));
    setErrors(current => ({...current, [name]: undefined, submit: undefined}));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (signupMutation.isPending) return;

    const validationErrors = validateSignup(values);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    signupMutation.mutate(
      {
        email: values.email,
        password: values.password,
        nickname: values.nickname.trim(),
      },
      {
        onSuccess: () => onSuccess(values.email),
        onError: error => {
          setErrors({
            submit: error instanceof HttpError && error.status === 409
              ? '이미 사용 중인 이메일 또는 닉네임입니다.'
              : '회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.',
          });
        },
      },
    );
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
          onFocus={() => setFocusedField('email')} onBlur={() => setFocusedField(null)}
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'signup-email-error' : undefined}
        />
        {focusedField === 'email' && <small className="auth-field-hint">example@domain.com 형식, 영문 2자 이상 도메인</small>}
        {errors.email && (
          <small id="signup-email-error" className="auth-field-error" role="alert">
            {errors.email}
          </small>
        )}
      </label>
      <label>
        비밀번호
        <input
          type="password"
          autoComplete="new-password"
          value={values.password}
          onChange={event => updateValue('password', event.target.value)}
          onFocus={() => setFocusedField('password')} onBlur={() => setFocusedField(null)}
          aria-invalid={Boolean(errors.password)}
          aria-describedby={errors.password ? 'signup-password-error' : undefined}
        />
        {focusedField === 'password' && <small className="auth-field-hint">3종 조합 8자 이상, 2종 조합 10자 이상</small>}
        {errors.password && (
          <small id="signup-password-error" className="auth-field-error" role="alert">
            {errors.password}
          </small>
        )}
      </label>
      <label>
        비밀번호 확인
        <input
          type="password"
          autoComplete="new-password"
          value={values.passwordConfirmation}
          onChange={event => updateValue('passwordConfirmation', event.target.value)}
          aria-invalid={Boolean(errors.passwordConfirmation)}
          aria-describedby={
            errors.passwordConfirmation ? 'signup-password-confirmation-error' : undefined
          }
        />
        {errors.passwordConfirmation && (
          <small
            id="signup-password-confirmation-error"
            className="auth-field-error"
            role="alert"
          >
            {errors.passwordConfirmation}
          </small>
        )}
      </label>
      <label>
        닉네임
        <input
          autoComplete="nickname"
          value={values.nickname}
          onChange={event => updateValue('nickname', event.target.value)}
          onFocus={() => setFocusedField('nickname')} onBlur={() => setFocusedField(null)}
          aria-invalid={Boolean(errors.nickname)}
          aria-describedby={errors.nickname ? 'signup-nickname-error' : undefined}
        />
        {focusedField === 'nickname' && <small className="auth-field-hint">2~30자, 한글·영문·숫자만 사용</small>}
        {errors.nickname && (
          <small id="signup-nickname-error" className="auth-field-error" role="alert">
            {errors.nickname}
          </small>
        )}
      </label>
      {errors.submit && <p className="auth-submit-error" role="alert">{errors.submit}</p>}
      <button className="auth-primary-button" type="submit" disabled={signupMutation.isPending}>
        {signupMutation.isPending ? '가입 중...' : '회원가입'}
      </button>
    </form>
  );
}
