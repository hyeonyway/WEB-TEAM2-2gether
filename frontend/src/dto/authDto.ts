export type SignupRequestDto = {
  email: string;
  password: string;
  nickname: string;
};

export type SignupResponseDto = {
  id: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';
};

export type LoginRequestDto = {
  email: string;
  password: string;
};

export type LoginResponseDto = {
  accessToken: string;
};

export type RefreshResponseDto = {
  accessToken: string;
};
