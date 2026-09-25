"use client";

import { useState } from "react";
import { ArrowDown, ArrowDownToLine, ArrowRight, Check, ChevronDown, CircleHelp, Disc3, Headphones, Link2, Menu, Music2, Play, ShieldCheck, Sparkles, Video, X, Zap } from "lucide-react";
import { parseYouTubeUrl } from "@/lib/youtube";

const videoQualities = ["2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD", "480p", "360p"];
const faq = [
  ["Какое качество можно скачать?", "Планируется выбор всех доступных разрешений до 4K, включая Full HD и 1440p. Список будет зависеть от исходного ролика. В демонстрации показаны примерные варианты."],
  ["Можно скачать только музыку?", "Да. Вкладка «Аудио» предусматривает MP3, M4A и WebM. MP3 потребует конвертации; увеличение битрейта не улучшает качество исходного звука."],
  ["Поддерживаются видео с доступом по ссылке?", "В требования включены публичные видео и Unlisted, доступные без входа в аккаунт. Фактическую доступность будет проверять сервер."],
  ["Скачивание уже работает?", "Это первый этап проекта: интерактивный интерфейс. Анализ YouTube, очередь обработки и выдача файлов будут подключены на следующем этапе."],
];

export default function Home() {
  const [url, setUrl] = useState("");
  const [mode, setMode] = useState<"video" | "audio">("video");
  const [format, setFormat] = useState("MP4");
  const [quality, setQuality] = useState(videoQualities[0]);
  const [demo, setDemo] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [menu, setMenu] = useState(false);

  function changeMode(next: "video" | "audio") {
    setMode(next); setFormat(next === "video" ? "MP4" : "MP3");
    setQuality(next === "video" ? videoQualities[0] : "320 kbps"); setNotice("");
  }
  function analyze(event: React.FormEvent) {
    event.preventDefault(); setNotice("");
    if (!parseYouTubeUrl(url)) { setError("Введите корректную ссылку на видео YouTube: youtube.com/watch?v=… или youtu.be/…"); return; }
    setError(""); setDemo(false);
    setNotice("Ссылка распознана. Анализ видео и скачивание подключим на следующем этапе. Пока можно посмотреть демопример ниже.");
  }
  function showDemo() { setDemo(true); setError(""); setNotice(""); }

  return (
    <div className="site-shell">
      <header className="header">
        <a className="brand" href="#" aria-label="Flow — главная"><span className="brand-icon"><ArrowDownToLine size={23}/></span>flow<span className="brand-dot">.</span></a>
        <nav className={menu ? "nav open" : "nav"} aria-label="Основная навигация">
          <a href="#downloader" onClick={() => setMenu(false)}>Загрузить</a><a href="#how" onClick={() => setMenu(false)}>Как это работает</a><a href="#faq" onClick={() => setMenu(false)}>Вопросы и ответы</a>
        </nav>
        <span className="header-label"><span/> Первый этап · Preview</span>
        <button className="mobile-menu icon-button" onClick={() => setMenu(!menu)} aria-label="Открыть меню" aria-expanded={menu}>{menu ? <X/> : <Menu/>}</button>
      </header>

      <main>
        <section className="hero" id="downloader">
          <div className="eyebrow"><span className="status-dot"/> МЕНЬШЕ ДЕЙСТВИЙ. БОЛЬШЕ КОНТЕНТА.</div>
          <h1>Твои видео.<br/>Твоя музыка. <span>Твой ритм.</span></h1>
          <p className="hero-description">Сохраняй любимое с YouTube в нужном формате.<br/>От музыки в наушниках до видео в 4K на большом экране.</p>

          <div className="download-card">
            <div className="card-top"><div className="tabs" role="tablist" aria-label="Тип загрузки">
              <button role="tab" aria-selected={mode === "video"} className={mode === "video" ? "active" : ""} onClick={() => changeMode("video")}><Video size={17}/> Видео</button>
              <button role="tab" aria-selected={mode === "audio"} className={mode === "audio" ? "active" : ""} onClick={() => changeMode("audio")}><Music2 size={17}/> Аудио</button>
            </div><span className="supported">YouTube <span>+ YouTube Music</span></span></div>
            <form onSubmit={analyze} noValidate>
              <label className="input-label" htmlFor="video-url">Ссылка на видео</label>
              <div className="url-row"><div className={`url-field ${error ? "invalid" : ""}`}><Link2 size={20}/><input id="video-url" type="url" value={url} onChange={e => { setUrl(e.target.value); setError(""); setNotice(""); setDemo(false); }} placeholder="Вставь ссылку с YouTube сюда" aria-invalid={!!error} aria-describedby={error ? "url-error" : undefined}/>{url && <button type="button" className="icon-button" aria-label="Очистить ссылку" onClick={() => { setUrl(""); setDemo(false); setError(""); setNotice(""); }}><X size={16}/></button>}</div><button className="primary-button" type="submit">Найти видео <ArrowRight size={18}/></button></div>
              {error && <p id="url-error" role="alert" className="error">{error}</p>}
            </form>
            <div className="input-hint"><span><ShieldCheck size={14}/> Публичные видео и доступ по ссылке</span><button onClick={showDemo}>Посмотреть пример <ArrowRight size={13}/></button></div>
            {notice && <div className="notice" role="status"><CircleHelp size={18}/><span>{notice}</span></div>}
            {demo && <div className="demo-panel">
              <div className="demo-label"><Sparkles size={13}/> ДЕМОПРИМЕР · НЕ ДАННЫЕ ВВЕДЁННОЙ ССЫЛКИ</div>
              <div className="media-info"><div className="thumbnail"><div className="sun"/><div className="mountain back"/><div className="mountain"/><Play size={22} fill="currentColor"/><span>04:32</span></div><div><span className="media-category">NATURE & SOUND</span><h3>Маленькое путешествие. Большие впечатления.</h3><p>Пример видео · 4 минуты · до 4K</p></div></div>
              <div className="options"><label>Формат<div className="select-wrap"><select value={format} onChange={e => {setFormat(e.target.value); if(mode === "audio") setQuality(e.target.value === "MP3" ? "320 kbps" : "Исходное качество");}}>{(mode === "video" ? ["MP4", "WebM"] : ["MP3", "M4A", "WebM"]).map(f => <option key={f}>{f}</option>)}</select><ChevronDown size={16}/></div></label><label>Качество<div className="select-wrap"><select value={quality} onChange={e => setQuality(e.target.value)}>{(mode === "video" ? videoQualities : format === "MP3" ? ["320 kbps", "256 kbps", "192 kbps", "128 kbps"] : ["Исходное качество"]).map(q => <option key={q}>{q}</option>)}</select><ChevronDown size={16}/></div></label><button className="prepare-button" onClick={() => setNotice(`Выбрано: ${format}, ${quality}. Это демонстрация интерфейса; создание файла пока не подключено.`)}><ArrowDownToLine size={18}/> Выбрать</button></div>
            </div>}
            <div className="card-footer"><span><Check size={13}/> Без регистрации</span><span><Check size={13}/> Видео и аудио</span><span><Check size={13}/> До 4K</span></div>
          </div>
          <div className="format-strip"><span>В ТВОЁМ ФОРМАТЕ</span><b>MP4</b><b>WEBM</b><b>MP3</b><b>M4A</b><i/><b className="quality-badge">4K <small>ULTRA HD</small></b></div>
        </section>

        <section className="features" aria-label="Возможности"><article><span className="feature-icon"><Video size={21}/></span><h3>Каждая деталь на месте</h3><p>1080p, 1440p или 4K — выбирай качество, доступное в исходном видео.</p></article><article><span className="feature-icon"><Headphones size={21}/></span><h3>Только то, что звучит</h3><p>Отдельная аудиодорожка для музыки, подкастов и твоих любимых выступлений.</p></article><article><span className="feature-icon"><Zap size={21}/></span><h3>Простой путь к файлу</h3><p>Ссылка, формат, загрузка. Всё нужное в одном месте, на любом экране.</p></article></section>

        <section className="how-section" id="how"><div className="section-heading"><div><span className="section-kicker">НИЧЕГО ЛИШНЕГО</span><h2>Три шага. И оно твоё.</h2></div><span className="outline-icon"><ArrowDown size={22}/></span></div><div className="steps">{[["01", "Скопируй ссылку", "Открой видео или трек на YouTube и скопируй его адрес."], ["02", "Выбери своё", "Укажи формат и доступное качество видео или аудио."], ["03", "Сохрани момент", "Дождись обработки и сохрани готовый файл на устройство."]].map(([n,t,d]) => <article key={n}><span className="step-number">{n}</span><h3>{t}</h3><p>{d}</p></article>)}</div><p className="preview-note"><Disc3 size={15}/> Сейчас доступен интерфейс. Реальная обработка файлов появится на следующем этапе.</p></section>

        <section className="faq-section" id="faq"><div><span className="section-kicker">ЕСТЬ ВОПРОС?</span><h2>Всё по делу.</h2><p>Несколько деталей,<br/>прежде чем начать.</p></div><div className="faq-list">{faq.map(([q,a]) => <details key={q}><summary>{q}<ChevronDown size={17}/></summary><p>{a}</p></details>)}</div></section>
      </main>
      <footer><a className="brand footer-brand" href="#"><span className="brand-icon"><ArrowDownToLine size={18}/></span>flow<span className="brand-dot">.</span></a><span>Твой контент. В твоём ритме.</span><span className="footer-version">Прототип · 2026</span></footer>
    </div>
  );
}
