import { useState, type FormEvent } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import verticalLogo from '../assets/brand/visearch-logo-vertical.png';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Input } from '../components/common';
import { ApiClientError } from '../services/apiClient';
import { login, register } from '../services/auth';
import { setAccessToken } from '../services/authToken';
import '../styles/auth.css';

interface AuthPageProps {
  mode: 'login' | 'register';
}

export function AuthPage({ mode }: AuthPageProps) {
  const isRegister = mode === 'register';
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;

    const form = new FormData(event.currentTarget);
    const email = String(form.get('email') ?? '').trim().toLowerCase();
    const password = String(form.get('password') ?? '');
    const name = String(form.get('name') ?? '').trim();
    const confirmPassword = String(form.get('confirmPassword') ?? '');

    setFormError('');
    setFieldErrors({});

    if (isRegister && password !== confirmPassword) {
      setFieldErrors({ confirmPassword: 'Mật khẩu xác nhận không khớp.' });
      return;
    }

    setIsSubmitting(true);
    try {
      const auth = isRegister
        ? await register({ name, email, password })
        : await login({ email, password });

      if (!auth?.accessToken) {
        throw new Error('Không thể đăng nhập. Vui lòng thử lại.');
      }

      setAccessToken(auth.accessToken);
      navigate('/', { replace: true });
    } catch (error) {
      if (error instanceof ApiClientError) {
        setFormError(error.message);
        setFieldErrors(error.fieldErrors);
      } else {
        setFormError(error instanceof Error ? error.message : 'Đã có lỗi xảy ra. Vui lòng thử lại.');
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-form-content" aria-labelledby="auth-heading">
        <Link className="auth-logo" to="/login" aria-label="ViSearch">
          <img className="auth-logo__image" src={verticalLogo} alt="ViSearch" />
        </Link>
        <h1 id="auth-heading">{isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}</h1>

        <form className="auth-form" onSubmit={handleSubmit}>
          {isRegister && (
            <Input
              key="name"
              name="name"
              label="Họ và tên"
              autoComplete="name"
              minLength={2}
              maxLength={50}
              required
              disabled={isSubmitting}
              error={fieldErrors.name}
            />
          )}

          <Input
            key={`email-${mode}`}
            name="email"
            type="email"
            label="Email"
            placeholder="ban@example.com"
            autoComplete="email"
            maxLength={50}
            required
            disabled={isSubmitting}
            error={fieldErrors.email}
          />

          <div className="auth-field">
            <label className="field-label" htmlFor="auth-password">Mật khẩu</label>
            <div className="auth-password-input">
              <Input
                key={`password-${mode}`}
                id="auth-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                placeholder={isRegister ? 'Ít nhất 8 ký tự' : undefined}
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                minLength={isRegister ? 8 : undefined}
                maxLength={72}
                required
                disabled={isSubmitting}
                error={fieldErrors.password}
              />
              <button
                className="auth-password-toggle"
                type="button"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                aria-pressed={showPassword}
                disabled={isSubmitting}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          {isRegister && (
            <Input
              key="confirmPassword"
              name="confirmPassword"
              type="password"
              label="Xác nhận mật khẩu"
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
              disabled={isSubmitting}
              error={fieldErrors.confirmPassword}
            />
          )}

          {formError && <p className="auth-form__error" role="alert">{formError}</p>}

          <Button className="auth-form__submit" size="large" type="submit" loading={isSubmitting}>
            {isSubmitting ? 'Đang xử lý...' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}
          </Button>
        </form>

        <div className="auth-switch">
          <Link to={isRegister ? '/login' : '/register'}>
            {isRegister ? 'Đăng nhập' : 'Tạo tài khoản'}
          </Link>
        </div>
      </section>
    </main>
  );
}
