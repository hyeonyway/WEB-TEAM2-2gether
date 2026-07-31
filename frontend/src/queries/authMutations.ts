import {login, logout, signup} from '../api/authApi';

export const authMutations = {
  signup: () => ({
    mutationFn: signup,
  }),
  login: () => ({
    mutationFn: login,
  }),
  logout: () => ({
    mutationFn: logout,
  }),
};
