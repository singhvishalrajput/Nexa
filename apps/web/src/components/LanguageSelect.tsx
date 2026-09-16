import { getLocale, Locale, setLocale, t } from "../services/locale";

export function LanguageSelect() {
  return <label class="nexa-language-select">{t("Language")}<select aria-label="Language / भाषा" value={getLocale()} onChange={event => setLocale(event.currentTarget.value as Locale)}><option value="en-IN">English</option><option value="hi-IN" lang="hi">हिन्दी</option></select></label>;
}
