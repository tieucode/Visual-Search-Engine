# ViSearch — Design System

> **Version 1.0** · Tài liệu ngôn ngữ thiết kế chuẩn cho toàn bộ frontend.  
> Mọi developer FE phải đọc và tuân thủ tài liệu này trước khi viết bất kỳ dòng UI nào.

---

## 1. Triết lý thiết kế

| Nguyên tắc | Mô tả |
|---|---|
| **Ít nhưng tinh** | Ưu tiên khoảng trắng, loại bỏ mọi thứ không truyền tải thông tin. |
| **Dark-first** | Giao diện dark mode là mặc định và tiêu chuẩn. Light mode là tùy chọn. |
| **Chuyển động có chủ đích** | Animation chỉ để hướng dẫn sự chú ý, không để trang trí thừa. |
| **Ngôn ngữ thống nhất** | Dùng token — không hard-code màu sắc, khoảng cách hay font trực tiếp. |
| **Ít trang, nhiều giá trị** | Gộp chức năng liên quan, tránh tạo trang chỉ để phân tách navigation. |

---

## 2. Brand Identity

- **Tên sản phẩm:** ViSearch
- **Personality:** Thông minh · Tối giản · Đáng tin cậy · Hiện đại
- **Màu nhận diện:** Deep Indigo → Violet (gradient chính)
- **Cảm giác:** Premium SaaS — giống Linear, Vercel, Raycast

---

## 3. Color Tokens

Toàn bộ màu sắc phải dùng CSS custom properties. **Cấm hard-code hex/rgb trực tiếp.**

### 3.1 Core Palette (OKLCH)

```css
:root {
  /* ── Backgrounds ── */
  --bg-base:      oklch(0.09 0.018 268);        /* Trang chính */
  --bg-surface:   oklch(0.12 0.022 268);        /* Card, panel */
  --bg-elevated:  oklch(0.16 0.026 268);        /* Modal, dropdown */
  --bg-overlay:   oklch(0.08 0.015 268 / 0.85); /* Dimmed backdrop */

  /* ── Brand ── */
  --brand:        oklch(0.55 0.24 268);
  --brand-hover:  oklch(0.62 0.22 268);
  --brand-active: oklch(0.48 0.26 268);
  --brand-glow:   oklch(0.55 0.24 268 / 0.35);

  /* ── Accent ── */
  --accent-violet: oklch(0.58 0.23 292);
  --accent-cyan:   oklch(0.72 0.15 200);
  --accent-green:  oklch(0.68 0.18 145);
  --accent-amber:  oklch(0.72 0.16 65);
  --accent-red:    oklch(0.62 0.22 27);

  /* ── Text ── */
  --text-primary:   oklch(0.97 0.005 268);
  --text-secondary: oklch(0.65 0.020 268);
  --text-muted:     oklch(0.45 0.015 268);
  --text-disabled:  oklch(0.35 0.010 268);

  /* ── Borders ── */
  --border-subtle:  oklch(1 0 0 / 0.07);
  --border-medium:  oklch(1 0 0 / 0.13);
  --border-strong:  oklch(0.55 0.24 268 / 0.40);

  /* ── Semantic ── */
  --color-success: var(--accent-green);
  --color-warning: var(--accent-amber);
  --color-error:   var(--accent-red);
  --color-info:    var(--brand);
}
```

### 3.2 Gradients

```css
--gradient-brand:     linear-gradient(135deg, var(--brand), var(--accent-violet));
--gradient-page-glow: radial-gradient(ellipse 80% 50% at 50% -5%,
                        oklch(0.55 0.24 268 / 0.20), transparent);
--gradient-text:      linear-gradient(135deg,
                        oklch(0.75 0.18 268), oklch(0.72 0.20 292));
```

### 3.3 Glassmorphism

```css
.glass {
  background: oklch(0.14 0.022 268 / 0.80);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--border-subtle);
}

.glass-brand {
  background: oklch(0.55 0.24 268 / 0.10);
  backdrop-filter: blur(12px);
  border: 1px solid oklch(0.55 0.24 268 / 0.25);
}
```

---

## 4. Typography

**Font ưu tiên:** `Inter Variable` → `Geist Variable` → system-ui

```css
--font-sans: "Inter Variable", "Geist Variable", ui-sans-serif, system-ui, sans-serif;
--font-mono: "Geist Mono", "JetBrains Mono", ui-monospace, monospace;
```

### Type Scale

| Token | Size | Weight | Use |
|---|---|---|---|
| `--text-display` | 3.5rem / 56px | 800 | Hero headings |
| `--text-4xl` | 2.25rem / 36px | 700 | Page titles |
| `--text-3xl` | 1.875rem / 30px | 700 | Section headings |
| `--text-2xl` | 1.5rem / 24px | 600 | Card headings |
| `--text-xl` | 1.25rem / 20px | 600 | Subheadings |
| `--text-lg` | 1.125rem / 18px | 500 | Large body |
| `--text-base` | 1rem / 16px | 400 | Body text |
| `--text-sm` | 0.875rem / 14px | 400 | Secondary text |
| `--text-xs` | 0.75rem / 12px | 400 | Labels, captions |

### Typography Rules

- Letter spacing heading: `-0.025em`
- Line height heading: `1.15`
- Line height body: `1.6`
- **Gradient text** chỉ dùng cho 1 cụm từ trong heading, không dùng toàn bộ heading

```css
.text-gradient {
  background: var(--gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
```

---

## 5. Spacing

Base **4px**:

| Token | Value | Dùng cho |
|---|---|---|
| `--space-1` | 4px | Micro gaps |
| `--space-2` | 8px | Compact padding |
| `--space-3` | 12px | Form padding |
| `--space-4` | 16px | Default padding |
| `--space-5` | 20px | Card padding sm |
| `--space-6` | 24px | Card padding |
| `--space-8` | 32px | Section gaps |
| `--space-10` | 40px | Large section gaps |
| `--space-12` | 48px | Page top padding |
| `--space-16` | 64px | Hero spacing |

---

## 6. Border Radius

```css
--radius-sm:   6px;
--radius-md:   10px;   /* Inputs */
--radius-lg:   14px;   /* Buttons */
--radius-xl:   18px;   /* Cards */
--radius-2xl:  24px;   /* Large cards, drop zones */
--radius-full: 9999px; /* Pills, avatars */
```

> ❌ Không dùng border-radius < 6px. Không mix radius giữa các component cùng cấp.

---

## 7. Shadows & Elevation

```css
--shadow-card:       0 1px 3px oklch(0 0 0 / 0.25), 0 4px 12px oklch(0 0 0 / 0.20);
--shadow-card-hover: 0 4px 16px oklch(0.55 0.24 268 / 0.20), 0 16px 40px oklch(0 0 0 / 0.30);
--shadow-modal:      0 8px 32px oklch(0 0 0 / 0.40), 0 24px 64px oklch(0 0 0 / 0.30);
--shadow-glow:       0 0 24px oklch(0.55 0.24 268 / 0.40), 0 4px 16px oklch(0.55 0.24 268 / 0.25);
--shadow-glow-sm:    0 0 12px oklch(0.55 0.24 268 / 0.30);
```

---

## 8. Animation & Motion

### Duration Tokens

```css
--duration-instant: 50ms;
--duration-fast:    150ms;   /* Hover states */
--duration-normal:  250ms;   /* Most transitions */
--duration-slow:    400ms;   /* Complex transitions */
--duration-slower:  600ms;   /* Page entrances */
```

### Easing

```css
--ease-out:    cubic-bezier(0, 0, 0.2, 1);
--ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
--ease-in-out: cubic-bezier(0.4, 0, 0.2, 1);
```

### Keyframes cốt lõi

```css
@keyframes fade-up {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes scale-in {
  from { opacity: 0; transform: scale(0.92); }
  to   { opacity: 1; transform: scale(1); }
}

@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 0 0 oklch(0.55 0.24 268 / 0.35); }
  50%       { box-shadow: 0 0 0 10px oklch(0.55 0.24 268 / 0); }
}

@keyframes shimmer {
  from { background-position: -200% center; }
  to   { background-position: 200% center; }
}
```

### Animation Rules

- Entrance: `fade-up` + `duration-slow` cho page-level content
- Hover: `translateY(-2px)` + shadow, `duration-fast`
- Scale bounce: `ease-spring`
- Skeleton: `shimmer` — không dùng spinner trừ async action
- ❌ Không animate màu sắc — chỉ animate `opacity`, `transform`, `box-shadow`

---

## 9. Component Patterns

### 9.1 Buttons

| Variant | Dùng khi |
|---|---|
| `primary` | CTA duy nhất trên view (Upload, Sign In) |
| `ghost` | Secondary actions (Cancel, Clear) |
| `outline` | Song song với primary (Upload more) |
| `destructive` | Xóa vĩnh viễn |

**Primary Button:**
- Background: `var(--gradient-brand)`
- Glow hover: `var(--shadow-glow)`
- Border-radius: `var(--radius-lg)`
- Height: `44px` / `52px` (large)
- Font-weight: `600`
- Hover: `translateY(-1px)` + glow
- Active: `translateY(0)`

### 9.2 Input Fields

- Background: `var(--bg-elevated)`
- Border: `1px solid var(--border-subtle)`
- Border-radius: `var(--radius-md)`
- Height: `44px`
- Focus: `var(--border-strong)` + `box-shadow: 0 0 0 3px oklch(0.55 0.24 268 / 0.15)`

### 9.3 Cards

- Background: `var(--bg-surface)`
- Border: `1px solid var(--border-subtle)`
- Border-radius: `var(--radius-xl)`
- Hover: `var(--shadow-card-hover)` + `translateY(-3px)` (`ease-spring`)

### 9.4 Badge / Pill

```css
.badge {
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
  border: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
  color: var(--text-secondary);
}
.badge-brand {
  background: oklch(0.55 0.24 268 / 0.12);
  border-color: oklch(0.55 0.24 268 / 0.30);
  color: oklch(0.78 0.16 268);
}
```

### 9.5 Drop Zone

```css
.drop-zone {
  border: 2px dashed var(--border-medium);
  border-radius: var(--radius-2xl);
  background: var(--bg-surface);
  transition: border-color 200ms, background 200ms, transform 200ms var(--ease-spring);
}
.drop-zone:hover,
.drop-zone.active {
  border-color: var(--brand);
  background: oklch(0.55 0.24 268 / 0.05);
  transform: scale(1.005);
  box-shadow: 0 0 40px oklch(0.55 0.24 268 / 0.12),
              inset 0 0 20px oklch(0.55 0.24 268 / 0.04);
}
```

### 9.6 Progress Bar

- Track: `var(--bg-elevated)`, height `6px`, `border-radius: full`
- Fill: `var(--gradient-brand)`, `transition: width 500ms ease-out`
- Cancel state: fill → `var(--accent-amber)`

### 9.7 Toast

- Vị trí: `bottom-right`
- Duration: success `3s`, error `5s`, warning `4s`
- Animation: slide-in từ phải + fade-in

---

## 10. Layout System

### 10.1 Page Structure

```
┌──────────────────────────────────────────┐
│ Header — fixed top, height 56px, glass   │
├──────────────────────────────────────────┤
│  Content area                            │
│  max-width: xem bảng bên dưới           │
│  padding: 0 24px (mobile: 0 16px)        │
└──────────────────────────────────────────┘
```

### 10.2 Content Max-Widths

| Context | Max-width |
|---|---|
| Form pages (login, upload) | `480px` |
| Centered content | `720px` |
| Standard page | `1024px` |
| Grid (search results) | `1280px` |

### 10.3 Responsive Breakpoints

```css
--bp-sm: 640px;
--bp-md: 768px;
--bp-lg: 1024px;
--bp-xl: 1280px;
```

### 10.4 Image Grid (Search Results)

- Mobile: 2 columns · Tablet: 3 columns · Desktop: 4–5 columns
- Gap: `var(--space-3)`
- Aspect ratio: `1 / 1`, `object-fit: cover`

---

## 11. Z-Index Layers

```css
--z-base:     0;
--z-raised:   10;
--z-dropdown: 100;
--z-sticky:   200;
--z-overlay:  300;
--z-modal:    400;
--z-toast:    500;
--z-tooltip:  600;
```

---

## 12. Scrollbar

```css
::-webkit-scrollbar { width: 5px; height: 5px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb {
  background: oklch(0.55 0.24 268 / 0.25);
  border-radius: 9999px;
}
::-webkit-scrollbar-thumb:hover {
  background: oklch(0.55 0.24 268 / 0.45);
}
```

---

## 13. Page Architecture (Routing)

Tối thiểu số trang, gộp chức năng liên quan:

| Route | Tên | Chức năng |
|---|---|---|
| `/login` | Login | Đăng nhập |
| `/register` | Register | Đăng ký |
| `/` | Search | Tìm kiếm ảnh — **trang core** (text, image, OCR dùng tabs) |
| `/upload` | Upload | Tải ảnh lên thư viện cá nhân |
| `/history` | History | Lịch sử tìm kiếm + thư viện ảnh |
| `/admin` | Admin | Quản lý hệ thống (role: admin) |

> Search page là trang chính. Không tạo sub-page riêng cho từng mode tìm kiếm.

---

## 14. Similarity Score Badge

```css
.score-high   { background: oklch(0.65 0.18 145 / 0.85); color: #fff; } /* ≥ 80% */
.score-medium { background: oklch(0.72 0.16 65  / 0.85); color: #fff; } /* 50–79% */
.score-low    { background: oklch(0.50 0.05 268 / 0.85); color: #fff; } /* < 50% */
```

---

## 15. Accessibility

- Focus: `outline: 2px solid var(--brand)` + `outline-offset: 2px`
- Contrast: ≥ 4.5:1 body text, ≥ 3:1 large text
- Touch target: minimum `44×44px`
- Loading: `aria-busy` hoặc spinner rõ ràng
- Error: `role="alert"`

---

## 16. Forbidden Practices ❌

1. Hard-code màu sắc — luôn dùng CSS token
2. Dùng `white` / `black` trực tiếp trên dark bg
3. Gradient text cho toàn bộ heading
4. Quá 2 font-weight khác nhau trong 1 component
5. Animate `color` / `background-color`
6. Box-shadow đen tối trên dark bg — dùng brand-tinted shadow
7. Tạo page mới khi fit được vào page hiện có
8. Border-radius < 6px
9. Inline style (trừ dynamic values từ data)

---

## 17. Page Mockups

### Login Page

> Split layout: Brand panel (left 40%) + Form panel (right 60%)

- **Left:** Dark bg + floating orbs indigo/violet + tên sản phẩm lớn + tagline + 3 feature pills
- **Right:** Heading "Welcome back", subtext, email input, password input + eye toggle, forgot password link, Sign In button (gradient), divider, link đăng ký

### Upload Page

> Centered single-column, max-width 720px

- Badge pill "Thư viện cá nhân" → Heading lớn → subtitle
- 3-step banner: Upload → AI Analyzes → Search easily
- Drop zone (dashed, large) với icon + text + Ctrl+V hint + format pills
- Thumbnail grid (khi đã chọn file)
- Upload button gradient full-width

---

*Cập nhật: 2026-10-01 · Dự án: Visual Search Engine*
