import { useEffect, useRef, useState } from 'react';
import { Check, ScanSearch, ScanText, Search, Type } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { useSearchWorkspace } from './SearchWorkspace';
import { ImageSearchModal } from './ImageSearchModal';
import '../../styles/search.css';

export function HeaderSearch() {
  const { textMode, setTextMode, query, setQuery, submitText, imageModalOpen, setImageModalOpen } = useSearchWorkspace();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const modeButton = useRef<HTMLButtonElement>(null);
  const imageButton = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const location = useLocation();
  const ModeIcon = textMode === 'text' ? Type : ScanText;
  const placeholder = textMode === 'text' ? 'mô tả bức ảnh mong muốn' : 'chữ trong ảnh bạn muốn tìm';

  useEffect(() => {
    setMenuOpen(false); setImageModalOpen(false);
  }, [location.pathname, location.search]);
  useEffect(() => {
    if (!menuOpen) return;
    const outside = (event: PointerEvent) => { if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false); };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [menuOpen]);

  return <>
    <form className="header-search" role="search" aria-label="Tìm hình ảnh" onSubmit={event => { event.preventDefault(); setMenuOpen(false); submitText(); }}>
      <button className="icon-button header-search__submit" type="submit" aria-label="Tìm kiếm" disabled={!query.trim()}><Search size={20} aria-hidden="true" /></button>
      <label htmlFor="header-query" className="sr-only">{placeholder}</label>
      <input ref={inputRef} id="header-query" className="header-search__input" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={placeholder} autoComplete="off" maxLength={500} />
      <div
        ref={menuRef} className="header-search__mode"
        onMouseEnter={() => setMenuOpen(true)}
        onMouseLeave={() => { if (!menuRef.current?.contains(document.activeElement)) setMenuOpen(false); }}
        onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setMenuOpen(false); }}
        onKeyDown={event => {
          if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); setMenuOpen(false); modeButton.current?.focus(); }
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault(); setMenuOpen(true);
            const direction = event.key === 'ArrowDown' ? 1 : -1;
            requestAnimationFrame(() => {
              const options = Array.from(menuRef.current?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]') || []);
              const index = options.indexOf(document.activeElement as HTMLButtonElement);
              const next = index < 0 ? (direction > 0 ? 0 : options.length - 1) : (index + direction + options.length) % options.length;
              options[next]?.focus();
            });
          }
        }}
      >
        <button ref={modeButton} className="icon-button" type="button" aria-label={`Chế độ tìm kiếm: ${textMode === 'text' ? 'Mô tả' : 'Chữ trong ảnh'}`} aria-haspopup="menu" aria-expanded={menuOpen} aria-controls="search-mode-menu" onClick={() => setMenuOpen(true)}><ModeIcon size={20} aria-hidden="true" /></button>
        {menuOpen && <div className="search-mode-menu" id="search-mode-menu" role="menu" aria-label="Chế độ tìm kiếm">
          {([{ id: 'text', label: 'Mô tả', icon: Type }, { id: 'ocr', label: 'Chữ trong ảnh', icon: ScanText }] as const).map(({ id, label, icon: Icon }) => <button key={id} type="button" role="menuitemradio" aria-checked={textMode === id} onClick={() => { setTextMode(id); setMenuOpen(false); inputRef.current?.focus(); }}><Icon size={18} aria-hidden="true" /><span>{label}</span>{textMode === id && <Check size={16} aria-hidden="true" />}</button>)}
        </div>}
      </div>
      <button ref={imageButton} className="icon-button header-search__image" type="button" aria-label="Tìm bằng ảnh" title="Tìm bằng ảnh" aria-haspopup="dialog" aria-expanded={imageModalOpen} aria-controls="image-search-dialog" onClick={() => { setMenuOpen(false); setImageModalOpen(!imageModalOpen); }}><ScanSearch size={23} strokeWidth={1.8} aria-hidden="true" /></button>
    </form>
    {imageModalOpen && <ImageSearchModal triggerRef={imageButton} />}
  </>;
}
