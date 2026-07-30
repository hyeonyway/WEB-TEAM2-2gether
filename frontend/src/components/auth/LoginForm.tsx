type LoginFormProps = {
  initialEmail?: string;
};

export default function LoginForm({initialEmail = ''}: LoginFormProps) {
  return (
    <form className="auth-form" onSubmit={event => event.preventDefault()}>
      <label>
        이메일
        <input autoComplete="email" defaultValue={initialEmail}/>
      </label>
      <label>
        비밀번호
        <input type="password" autoComplete="current-password"/>
      </label>
      <button className="auth-primary-button" type="submit">로그인</button>
    </form>
  );
}
