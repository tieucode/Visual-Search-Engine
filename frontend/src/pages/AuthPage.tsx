import { useState, type FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff, Images, ScanSearch, ScanText, Sparkles } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { Badge, Button, Input } from '../components/common';
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
        throw new Error('Phản hồi đăng nhập không có access token.');
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
      <aside className="auth-brand" aria-label="Giới thiệu Visual Search Engine">
        <div className="auth-brand__orb auth-brand__orb--one" aria-hidden="true" />
        <div className="auth-brand__orb auth-brand__orb--two" aria-hidden="true" />
        <div className="auth-brand__content">
          <Link className="auth-logo" to="/login" aria-label="Visual Search Engine - về trang đăng nhập">
            <span className="auth-logo__mark"><ScanSearch size={24} strokeWidth={2} /></span>
            <span>Visual <span className="text-gradient">Search Engine</span></span>
          </Link>

          <div className="auth-brand__message animate-enter">
            <Badge variant="brand"><Sparkles size={13} aria-hidden="true" /> TÌM KIẾM THÔNG MINH</Badge>
            <h1>Mọi hình ảnh.<br /><span className="text-gradient">Một nơi để tìm.</span></h1>
            <p>Lưu trữ, khám phá và tìm lại hình ảnh của bạn bằng sức mạnh AI.</p>
            <div className="auth-features" aria-label="Tính năng nổi bật">
              <span><Images size={16} aria-hidden="true" /> Tìm bằng ảnh</span>
              <span><ScanSearch size={16} aria-hidden="true" /> Tìm bằng mô tả</span>
              <span><ScanText size={16} aria-hidden="true" /> Tìm bằng văn bản</span>
            </div>
          </div>

          <p className="auth-brand__footer">Visual Search Engine · Trải nghiệm tìm kiếm hình ảnh mới</p>
        </div>
      </aside>

      <section className="auth-form-panel" aria-labelledby="auth-heading">
        <div className="auth-form-content animate-enter">
          <Link className="auth-logo auth-logo--mobile" to="/login" aria-label="Visual Search Engine - về trang đăng nhập">
            <span className="auth-logo__mark"><ScanSearch size={22} strokeWidth={2} /></span>
            <span>Visual <span className="text-gradient">Search Engine</span></span>
          </Link>

          <Badge variant="brand">{isRegister ? 'BẮT ĐẦU CÙNG VISUAL SEARCH ENGINE' : 'CHÀO MỪNG TRỞ LẠI'}</Badge>
          <h2 id="auth-heading">{isRegister ? 'Tạo tài khoản' : 'Welcome back'}</h2>
          <p className="auth-form-content__intro">
            {isRegister
              ? 'Tạo tài khoản để bắt đầu khám phá thư viện hình ảnh của bạn.'
              : 'Đăng nhập để tiếp tục khám phá thư viện hình ảnh của bạn.'}
          </p>

          <form className="auth-form" onSubmit={handleSubmit}>
            {isRegister && (
              <Input
                key="name"
                name="name"
                label="Họ và tên"
                placeholder="Nguyễn Văn A"
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
                  placeholder={isRegister ? 'Ít nhất 8 ký tự' : 'Nhập mật khẩu của bạn'}
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
                placeholder="Nhập lại mật khẩu"
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
              {!isSubmitting && <ArrowRight size={18} aria-hidden="true" />}
            </Button>
          </form>

          <div className="auth-switch">
            <span>{isRegister ? 'Đã có tài khoản?' : 'Chưa có tài khoản?'}</span>{' '}
            <Link to={isRegister ? '/login' : '/register'}>
              {isRegister ? 'Đăng nhập' : 'Đăng ký miễn phí'}
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
