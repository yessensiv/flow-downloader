import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Flow — видео и музыка в твоём ритме",
  description: "Интерфейс загрузки видео и аудио с YouTube. Выбор формата и качества до 4K.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ru"><body>{children}</body></html>;
}
