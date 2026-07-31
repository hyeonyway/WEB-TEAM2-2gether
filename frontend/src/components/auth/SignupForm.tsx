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

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validateSignup(
  values: SignupRequestDto & {passwordConfirmation: string},
): SignupErrors {
  const errors: SignupErrors = {};

  if (!emailPattern.test(values.email) || values.email.length > 255) {
    errors.email = '올바른 이메일 주소를 입력해 주세요.';
  }
  if (values.password.length < 8 || values.password.length > 128) {
    errors.password = '비밀번호는 8자 이상 128자 이하로 입력해 주세요.';
  }
  if (values.password !== values.passwordConfirmation) {
    errors.passwordConfirmation = '비밀번호가 일치하지 않습니다.';
  }
  if (values.nickname.trim().length < 2 || values.nickname.trim().length > 30) {
    errors.nickname = '닉네임은 2자 이상 30자 이하로 입력해 주세요.';
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
          aria-invalid={Boolean(errors.email)}
          aria-describedby={errors.email ? 'signup-email-error' : undefined}
        />
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
          aria-invalid={Boolean(errors.password)}
          aria-describedby={errors.password ? 'signup-password-error' : undefined}
        />
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
          aria-invalid={Boolean(errors.nickname)}
          aria-describedby={errors.nickname ? 'signup-nickname-error' : undefined}
        />
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
