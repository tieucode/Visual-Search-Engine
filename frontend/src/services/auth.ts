import { request } from './apiClient';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  name: string;
}

export interface AuthData {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  expiresAt: string;
  user: {
    id: string;
    email: string;
    name: string;
    role: 'USER' | 'ADMIN';
  };
}

export async function login(data: LoginRequest): Promise<AuthData> {
  const response = await request<AuthData, LoginRequest>({ method: 'POST', url: '/auth/login', data });
  return response.data;
}

export async function register(data: RegisterRequest): Promise<AuthData> {
  const response = await request<AuthData, RegisterRequest>({ method: 'POST', url: '/auth/register', data });
  return response.data;
}
