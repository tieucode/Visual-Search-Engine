import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { UploadCloud } from 'lucide-react';
import { MAX_IMAGES } from '../../hooks/useImageQueue';
import { formatBytes, formatNumber } from '../../utils/format';

interface DropZoneProps {
  count: number;
  totalBytes: number;
  disabled: boolean;
  onFiles: (files: File[]) => void;
}

function hasFiles(event: DragEvent): boolean {
  return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}

function isEditable(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  return !!element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.isContentEditable);
}

export function DropZone({ count, totalBytes, disabled, onFiles }: DropZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [active, setActive] = useState(false);
  const onFilesRef = useRef(onFiles);
  const disabledRef = useRef(disabled);
  onFilesRef.current = onFiles;
  disabledRef.current = disabled;

  useEffect(() => {
    let depth = 0;

    const onEnter = (event: DragEvent) => {
      if (!hasFiles(event)) return;
      event.preventDefault();
      depth += 1;
      if (!disabledRef.current) setActive(true);
    };
    const onOver = (event: DragEvent) => {
      if (!hasFiles(event)) return;
      event.preventDefault();
      if (event.dataTransfer) event.dataTransfer.dropEffect = disabledRef.current ? 'none' : 'copy';
    };
    const onLeave = (event: DragEvent) => {
      if (!hasFiles(event)) return;
      depth = Math.max(0, depth - 1);
      if (depth === 0) setActive(false);
    };
    const onDrop = (event: DragEvent) => {
      if (!hasFiles(event)) return;
      event.preventDefault();
      depth = 0;
      setActive(false);
      const files = Array.from(event.dataTransfer?.files ?? []);
      if (files.length > 0 && !disabledRef.current) onFilesRef.current(files);
    };
    const onPaste = (event: ClipboardEvent) => {
      if (disabledRef.current || isEditable(event.target)) return;
      const files = Array.from(event.clipboardData?.files ?? []).filter((file) => file.type.startsWith('image/'));
      if (files.length === 0) return;
      event.preventDefault();
      const stamp = Date.now();
      onFilesRef.current(files.map((file, index) => {
        const extension = file.type.split('/')[1] || 'png';
        return new File([file], `pasted-${stamp}-${index + 1}.${extension}`, { type: file.type, lastModified: stamp });
      }));
    };

    window.addEventListener('dragenter', onEnter);
    window.addEventListener('dragover', onOver);
    window.addEventListener('dragleave', onLeave);
    window.addEventListener('drop', onDrop);
    document.addEventListener('paste', onPaste);
    return () => {
      window.removeEventListener('dragenter', onEnter);
      window.removeEventListener('dragover', onOver);
      window.removeEventListener('dragleave', onLeave);
      window.removeEventListener('drop', onDrop);
      document.removeEventListener('paste', onPaste);
    };
  }, []);

  const openPicker = () => { if (!disabled) inputRef.current?.click(); };
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      openPicker();
    }
  };

  return (
    <div
      className="drop-zone upload-drop"
      data-active={active}
      role="button"
      tabIndex={0}
      aria-label="Chọn ảnh để tải lên"
      aria-disabled={disabled}
      onClick={openPicker}
      onKeyDown={onKeyDown}
    >
      {count > 0 && (
        <div className="upload-drop__stats" aria-label="Ảnh đã chọn">
          <span className="upload-drop__stat-count">{formatNumber(count)} / {formatNumber(MAX_IMAGES)} ảnh</span>
          <span className="upload-drop__stat-size">{formatBytes(totalBytes)}</span>
        </div>
      )}

      <span className="upload-drop__icon"><UploadCloud size={24} strokeWidth={1.5} aria-hidden="true" /></span>
      <p className="upload-drop__title">Kéo ảnh vào đây hoặc <span className="upload-drop__link">chọn ảnh</span></p>
      <div className="upload-drop__meta">
        <span>JPG, PNG, WEBP</span>
        <span aria-hidden="true">·</span>
        <span>Dán bằng <kbd className="kbd">Ctrl + V</kbd></span>
      </div>

      <input
        ref={inputRef}
        className="sr-only"
        type="file"
        disabled={disabled}
        multiple
        accept="image/jpeg,image/png,image/webp"
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          if (!disabled && files.length > 0) onFiles(files);
        }}
      />
    </div>
  );
}
