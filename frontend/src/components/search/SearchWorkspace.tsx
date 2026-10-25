import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useSearchFeed } from '../../hooks/useSearchFeed';
import { recommendedDemo, searchDemo } from '../../services/mockSearchService';
import { nextImagePage, nextOcrPage, searchByImage, searchByOcr } from '../../services/searchService';
import { nextSearchPage, searchByImage, searchByText } from '../../services/searchService';
import type { SearchMode } from '../../types/search';

type QueryImage = { file: File; width: number; height: number };
type ResultMode = SearchMode | 'recommend';

function useWorkspace() {
  const feed = useSearchFeed();
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState<ResultMode>('recommend');
  const [textMode, setTextMode] = useState<'text' | 'ocr'>('text');
  const [query, setQuery] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [queryImage, setQueryImage] = useState<QueryImage | null>(null);
  const [preview, setPreview] = useState('');
  const [imageModalOpen, setImageModalOpen] = useState(false);
  const rootUrl = useRef('');
  const scrollY = useRef(0);
  const [likes, setLikes] = useState<Set<string>>(() => new Set());

  useEffect(() => {
    if (!queryImage) { setPreview(''); return; }
    const url = URL.createObjectURL(queryImage.file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [queryImage]);

  const loadRecommendations = () => {
    setMode('recommend');
    void feed.start(signal => recommendedDemo(signal), (page, signal) => recommendedDemo(signal, page.nextCursor!));
  };
  const loadText = (nextMode: 'text' | 'ocr', text: string) => {
    setMode(nextMode);
    setSubmittedQuery(text);
    setMode(nextMode); setSubmittedQuery(text);
    if (nextMode === 'text') {
      void feed.start(signal => searchByText(text, signal), nextSearchPage);
    } else {
      void feed.start(signal => searchDemo('ocr', text, signal), (page, signal) => searchDemo('ocr', text, signal, page.nextCursor!));
    }
  };

    if (nextMode === 'ocr') {
      void feed.start(
          signal => searchByOcr(text, signal),
          nextOcrPage,
      );
      return;
    }

    void feed.start(
      signal => searchDemo('text', text, signal),
      (page, signal) => searchDemo('text', text, signal, page.nextCursor!),
    );
  };
  // Keep results alive across detail navigation. Defer initial work for StrictMode replay.
  useEffect(() => {
    if (location.pathname !== '/') return;
    const url = '/' + location.search;
    if (rootUrl.current === url) return;
    const timer = window.setTimeout(() => {
      rootUrl.current = url;
      scrollY.current = 0;
      window.scrollTo(0, 0);
      const params = new URLSearchParams(location.search);
      const nextMode = params.get('mode');
      const text = (params.get('q') || '').trim();
      if (nextMode === 'image' && queryImage) {
        setMode('image');
        void feed.start(signal => searchByImage(queryImage.file, signal), nextSearchPage);
      } else if ((nextMode === 'text' || nextMode === 'ocr') && text) {
        setTextMode(nextMode); setQuery(text); loadText(nextMode, text);
      } else {
        setQuery(''); loadRecommendations();
        if (nextMode === 'image') setImageModalOpen(true);
      }
    }, 0);
    return () => window.clearTimeout(timer);
  }, [location.pathname, location.search]);

  const submitText = () => {
    const text = query.trim();
    if (!text || feed.loading) return;
    const url = '/?' + new URLSearchParams({ mode: textMode, q: text });
    rootUrl.current = url; scrollY.current = 0;
    setImageModalOpen(false); loadText(textMode, text);
    navigate(url); window.scrollTo(0, 0);
  };

  const submitImage = (image: QueryImage) => {
    rootUrl.current = '/?mode=image'; scrollY.current = 0;
    setQueryImage(image); setMode('image'); setImageModalOpen(false);
    void feed.start(signal => searchByImage(image.file, signal), nextSearchPage);
    navigate('/?mode=image'); window.scrollTo(0, 0);
  };

  const retry = () => {
    if (mode === 'image' && queryImage) void feed.start(signal => searchByImage(queryImage.file, signal), nextSearchPage);
    else if (mode === 'text' || mode === 'ocr') loadText(mode, submittedQuery);
    else loadRecommendations();
  };
  const toggleLike = (id: string) => setLikes(current => {
    const next = new Set(current);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });
  return { feed, mode, textMode, setTextMode, query, setQuery, submittedQuery, queryImage, preview, imageModalOpen, setImageModalOpen, submitText, submitImage, retry, rootUrl, scrollY, likes, toggleLike };
}

const SearchContext = createContext<ReturnType<typeof useWorkspace> | null>(null);
export function SearchWorkspace({ children }: { children: ReactNode }) {
  const workspace = useWorkspace();
  return <SearchContext.Provider value={workspace}>{children}</SearchContext.Provider>;
}
export function useSearchWorkspace() {
  const context = useContext(SearchContext);
  if (!context) throw new Error('SearchWorkspace is required');
  return context;
}

