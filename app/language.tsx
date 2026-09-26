"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { translate, type Language } from '@/lib/translations';

const LanguageContext = createContext({ lang: 'ru' as Language, setLang: (_lang: Language) => {} });

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, updateLanguage] = useState<Language>('ru');
  useEffect(() => {
    try { if (localStorage.getItem('flow-language') === 'en') updateLanguage('en'); } catch {}
  }, []);
  useEffect(() => {
    document.documentElement.lang = lang;
    document.title = lang === 'en' ? 'Flow — your videos, your music, your rhythm' : 'Flow — видео и музыка в твоём ритме';
    const description = document.querySelector('meta[name="description"]');
    description?.setAttribute('content', lang === 'en' ? 'Download YouTube video and audio. Choose your format and quality up to 4K.' : 'Интерфейс загрузки видео и аудио с YouTube. Выбор формата и качества до 4K.');
  }, [lang]);
  function setLang(next: Language) {
    updateLanguage(next);
    try { localStorage.setItem('flow-language', next); } catch {}
  }
  return <LanguageContext.Provider value={{ lang, setLang }}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const { lang, setLang } = useContext(LanguageContext);
  function t<T>(text: T): T { return (typeof text === 'string' ? translate(text, lang) : text) as T; }
  return { lang, setLang, t };
}
