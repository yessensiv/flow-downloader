"use client";
import { useLanguage } from "./language";

import { useEffect, useRef, useState } from "react";
import { MediaResult } from './media-result';
import { DownloadAction } from './download-action';
import type { MediaInfo } from '@/lib/media';
import { ArrowDown, ArrowDownToLine, ArrowRight, Check, ChevronDown, CircleHelp, Disc3, Headphones, Link2, LoaderCircle, Menu, Music2, Play, ShieldCheck, Sparkles, Video, X, Zap } from "lucide-react";
import { parseYouTubeUrl } from "@/lib/youtube";

const videoQualities = ["2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD", "480p", "360p"];
const faq = [
  ["Какое качество можно скачать?", "После анализа показываем доступные разрешения до 4K, включая Full HD и 1440p. Список зависит от исходного ролика и формата."],
  ["Можно скачать только музыку?", "Да. Выберите «Аудио»: M4A и WebM сохраняют исходную дорожку, MP3 создаётся с выбранным битрейтом от 128 до 320 кбит/с."],
  ["Поддерживаются видео с доступом по ссылке?", "Да, если ролик открывается по ссылке без входа в аккаунт. Приватные видео и ролики с требованием авторизации не поддерживаются."],
  ["Скачивание уже работает?", "Да, видео можно подготовить и сохранить со звуком в выбранном качестве. Размер — до 2 ГБ, готовый файл доступен 5 минут. Также доступны отдельное аудио и MP3."],
];

export default function Home() {
  const { lang, setLang, t } = useLanguage();
  const en = lang === "en";
  const [url, setUrl] = useState("");
  const [mode, setMode] = useState<"video" | "audio">("video");
  const [format, setFormat] = useState("MP4");
  const [quality, setQuality] = useState(videoQualities[0]);
  const [demo, setDemo] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [menu, setMenu] = useState(false);
  const [media,setMedia] = useState<MediaInfo | null>(null);
  const [loading,setLoading] = useState(false);
  const [urlFocused,setUrlFocused] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const urlInput = useRef<HTMLInputElement>(null);
  useEffect(() => () => pending.current?.abort(), []);
  useEffect(() => { if (urlFocused) urlInput.current?.focus(); }, [urlFocused]);
  function resetResult() {
    pending.current?.abort(); pending.current = null;
    setLoading(false); setMedia(null); setDemo(false); setError(''); setNotice('');
  }

  function changeMode(next: "video" | "audio") {
    setMode(next); setFormat(next === "video" ? "MP4" : "MP3");
    setQuality(next === "video" ? videoQualities[0] : "320 kbps"); setNotice("");
  }
  async function analyze(event: React.FormEvent) {
    event.preventDefault(); resetResult();
    if (!parseYouTubeUrl(url)) { setError("Введите корректную ссылку на видео YouTube: youtube.com/watch?v=… или youtu.be/…"); return; }
    const controller = new AbortController(); pending.current = controller; setLoading(true);
    const timer = setTimeout(() => controller.abort(),65000);
    try {
      const response = await fetch('/api/analyze',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url}),signal:controller.signal});
      const data = await response.json();
      if (pending.current !== controller) return;
      if (!response.ok) throw new Error(data.error || 'Не удалось проанализировать видео.');
      setMedia(data);
    } catch (failure) {
      if (pending.current === controller) setError(controller.signal.aborted ? 'Время ожидания истекло. Попробуйте ещё раз.' : failure instanceof Error ? failure.message : 'Не удалось связаться с сервером.');
    } finally {
      clearTimeout(timer);
      if (pending.current === controller) {setLoading(false);pending.current=null;}
    }
  }
  function showDemo() { resetResult(); setDemo(true); }

  return (
    <div className="site-shell">
      <header className="header">
        <a className="brand" href="#" aria-label={t("Flow — главная")}><span className="brand-icon"><ArrowDownToLine size={23}/></span>flow<span className="brand-dot">.</span></a>
        <nav className={menu ? "nav open" : "nav"} aria-label={t("Основная навигация")}>
          <a href="#downloader" onClick={() => setMenu(false)}>{t("Загрузить")}</a><a href="#how" onClick={() => setMenu(false)}>{t("Как это работает")}</a><a href="#faq" onClick={() => setMenu(false)}>{t("Вопросы и ответы")}</a>
        </nav>
        <span className="header-label"><span/> {t(en ? "Video and music — made simple" : "Видео и музыка — просто")}</span>
        <div className="language-switch" role="group" aria-label={en ? 'Language' : 'Язык'}><button type="button" aria-pressed={lang === 'ru'} className={lang === "ru" ? "active" : ""} onClick={() => setLang("ru")}>RU</button><button type="button" aria-pressed={lang === 'en'} className={lang === "en" ? "active" : ""} onClick={() => setLang("en")}>EN</button></div>
        <button className="mobile-menu icon-button" onClick={() => setMenu(!menu)} aria-label={t("Открыть меню")} aria-expanded={menu}>{menu ? <X/> : <Menu/>}</button>
      </header>

      <main>
        <section className="hero" id="downloader">
          <div className="eyebrow"><span className="status-dot"/> {t(en ? "LESS FRICTION. MORE CONTENT." : "МЕНЬШЕ ДЕЙСТВИЙ. БОЛЬШЕ КОНТЕНТА.")}</div>
          <h1>{en ? <>Your videos.<br/>Your music. <span>Your rhythm.</span></> : <>{t("Твои видео.")}<br/>{t("Твоя музыка. ")}<span>{t("Твой ритм.")}</span></>}</h1>
          <p className="hero-description">{en ? <>Save your favorite YouTube content in the format you need.<br/>From music in your headphones to 4K video on the big screen.</> : <>{t("Сохраняй любимое с YouTube в нужном формате.")}<br/>{t("От музыки в наушниках до видео в 4K на большом экране.")}</>}</p>

          <div className="download-card">
            <div className="card-top"><div className="tabs" data-mode={mode} role="tablist" aria-label={t("Тип загрузки")}>
              <button role="tab" aria-selected={mode === "video"} className={mode === "video" ? "active" : ""} onClick={() => changeMode("video")}><Video size={17}/> {t(en ? "Video" : "Видео")}</button>
              <button role="tab" aria-selected={mode === "audio"} className={mode === "audio" ? "active" : ""} onClick={() => changeMode("audio")}><Music2 size={17}/> {t(en ? "Audio" : "Аудио")}</button>
            </div><span className="supported">YouTube <span>+ YouTube Music</span></span></div>
            <form onSubmit={analyze} noValidate autoComplete="off">
              <label className="input-label" htmlFor="video-url">{t(en ? `YouTube ${mode === 'video' ? 'video' : 'track'} link` : `Ссылка на ${mode === 'video' ? 'видео' : 'трек'} YouTube`)}</label>
              <div className={`url-row ${parseYouTubeUrl(url) && !urlFocused ? 'url-row-filled' : ''}`}><div className={`url-field ${error ? "invalid" : ""} ${parseYouTubeUrl(url) && !urlFocused ? "url-filled" : ""}`}><Link2 size={20}/>{parseYouTubeUrl(url) && !urlFocused ? <button type="button" className="url-summary" onClick={() => setUrlFocused(true)} aria-label={t(en ? "Edit link" : "Изменить ссылку")} title={t(en ? "Click to edit" : "Нажмите, чтобы изменить ссылку")}><span>{t(url.includes('youtu.be') ? 'youtu.be' : url.includes('music.youtube.com') ? 'YouTube Music' : 'youtube.com')}</span><span className="url-summary-state"><Check size={14}/> {t(en ? "Link added" : "Ссылка добавлена")}</span></button> : <input ref={urlInput} id="video-url" name="youtube-video-url" type="text" inputMode="url" autoComplete="off" autoCorrect="off" autoCapitalize="none" spellCheck={false} value={url} onFocus={() => setUrlFocused(true)} onBlur={() => setUrlFocused(false)} onChange={e => { resetResult(); setUrl(e.target.value); }} placeholder={t(en ? "Paste a YouTube link here" : "Вставь ссылку с YouTube сюда")} aria-invalid={!!error} aria-describedby={error ? "url-error" : undefined}/ >}{url && <button type="button" className="icon-button" aria-label={t(en ? "Clear link" : "Очистить ссылку")} onClick={() => { resetResult(); setUrl(''); setUrlFocused(true); }}><X size={16}/></button>}</div><button className="primary-button" type="submit" disabled={loading}>{t(loading ? (en ? 'Finding options…' : 'Ищем варианты…') : (en ? 'Show options' : 'Показать варианты'))} <ArrowRight size={18}/></button></div>
              {error && <p id="url-error" role="alert" className="error">{t(en && error === "Введите корректную ссылку на видео YouTube: youtube.com/watch?v=… или youtu.be/…" ? "Enter a valid YouTube link: youtube.com/watch?v=… or youtu.be/…" : error)}</p>}
            </form>
            <div className="input-hint"><span><ShieldCheck size={14}/> {t(en ? "Public and unlisted videos" : "Открытые видео и ролики по ссылке")}</span><button onClick={showDemo}>{t(en ? "See an example" : "Посмотреть пример")} <ArrowRight size={13}/></button></div>
            {notice && <div className="notice" role="status"><CircleHelp size={18}/><span>{t(notice)}</span></div>}
            {loading && <div className="analysis-loading" role="status"><LoaderCircle className="flow-spinner" size={26}/><div><strong>{t(en ? "Finding your video" : "Находим ваше видео")}</strong><p>{t(en ? "Checking available quality and audio. This usually takes a few seconds." : "Проверяем доступное качество и звук. Обычно это несколько секунд.")}</p></div></div>}
            {media && <MediaResult key={`${media.id}:${mode}`} media={media} mode={mode}/>}
            {!media && <DownloadAction key={mode} mode={mode} restoreOnly/>}
            {demo && <div className="demo-panel">
              <div className="demo-label"><Sparkles size={13}/>{t(" Пример результата · для знакомства")}</div>
              <div className="media-info"><div className="thumbnail"><div className="sun"/><div className="mountain back"/><div className="mountain"/><Play size={22} fill="currentColor"/><span>04:32</span></div><div><span className="media-category">NATURE & SOUND</span><h3>{t("Маленькое путешествие. Большие впечатления.")}</h3><p>{t("Пример видео · 4 минуты · до 4K")}</p></div></div>
              <div className="options"><label>{t("Формат")}<div className="select-wrap"><select value={format} onChange={e => {setFormat(e.target.value); if(mode === "audio") setQuality(e.target.value === "MP3" ? "320 kbps" : "Исходное качество");}}>{(mode === "video" ? ["MP4", "WebM"] : ["MP3", "M4A", "WebM"]).map(f => <option key={f}>{t(f)}</option>)}</select><ChevronDown size={16}/></div></label><label>{t("Качество")}<div className="select-wrap"><select value={quality} onChange={e => setQuality(e.target.value)}>{(mode === "video" ? videoQualities : format === "MP3" ? ["320 kbps", "256 kbps", "192 kbps", "128 kbps"] : ["Исходное качество"]).map(q => <option key={q} value={q}>{t(q)}</option>)}</select><ChevronDown size={16}/></div></label><button className="prepare-button" onClick={() => setNotice(`Выбрано: ${format}, ${quality}. Это пример. Вставьте свою ссылку выше, чтобы скачать настоящее видео.`)}><ArrowDownToLine size={18}/>{t(" Выбрать")}</button></div>
            </div>}
            <div className="card-footer"><span><Check size={13}/>{t(" Без регистрации")}</span><span><Check size={13}/>{t(" Видео и аудио")}</span><span><Check size={13}/>{t(" До 4K")}</span></div>
          </div>
          <div className="format-strip"><span>{t("В ТВОЁМ ФОРМАТЕ")}</span><b>MP4</b><b>WEBM</b><b>MP3</b><b>M4A</b><i/><b className="quality-badge">4K <small>ULTRA HD</small></b></div>
        </section>

        <section className="features" aria-label={t("Возможности")}><article><span className="feature-icon"><Video size={21}/></span><h3>{t("Каждая деталь на месте")}</h3><p>{t("1080p, 1440p или 4K — выбирай качество, доступное в исходном видео.")}</p></article><article><span className="feature-icon"><Headphones size={21}/></span><h3>{t("Только то, что звучит")}</h3><p>{t("Музыка и подкасты без видео. Сохраняй исходный звук или выбирай привычный MP3.")}</p></article><article><span className="feature-icon"><Zap size={21}/></span><h3>{t("Простой путь к файлу")}</h3><p>{t("Ссылка, формат, загрузка. Всё нужное в одном месте, на любом экране.")}</p></article></section>

<section className="how-section" id="how"><div className="section-heading"><div><span className="section-kicker">{t("НИЧЕГО ЛИШНЕГО")}</span><h2>{t("Три шага. И оно твоё.")}</h2></div><span className="outline-icon"><ArrowDown size={22}/></span></div><div className="steps">{[["01", "Скопируй ссылку", "Открой видео или трек на YouTube и скопируй его адрес."], ["02", "Выбери своё", "Укажи формат и доступное качество видео или аудио."], ["03", "Сохрани момент", "Дождись обработки и сохрани готовый файл на устройство."]].map(([n,title,d]) => <article key={n}><span className="step-number">{t(n)}</span><h3>{t(title)}</h3><p>{t(d)}</p></article>)}</div><p className="preview-note"><Disc3 size={15}/>{t(" Видео со звуком или только аудио — выбирай то, что нужно.")}</p></section>

        <section className="faq-section" id="faq"><div><span className="section-kicker">{t("ЕСТЬ ВОПРОС?")}</span><h2>{t("Всё по делу.")}</h2><p>{t("Несколько деталей,")}<br/>{t("прежде чем начать.")}</p></div><div className="faq-list">{faq.map(([q,a]) => <details key={q}><summary>{t(q)}<ChevronDown size={17}/></summary><p>{t(a)}</p></details>)}</div></section>
      </main>
      <footer><a className="brand footer-brand" href="#"><span className="brand-icon"><ArrowDownToLine size={18}/></span>flow<span className="brand-dot">.</span></a><span>{t("Твой контент. В твоём ритме.")}</span><span className="footer-version">{t("Прототип · 2026")}</span></footer>
    </div>
  );
}
