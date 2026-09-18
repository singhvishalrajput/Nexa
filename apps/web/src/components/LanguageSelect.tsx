import { getLocale, Locale, setLocale, t } from "../services/locale";
import "ojs/ojselectcombobox";
export function LanguageSelect() {
  return <oj-select-one class="experience-language" labelHint={t("Language")} labelEdge="inside" value={getLocale()} onvalueChanged={event => { if (event.detail.value && event.detail.value !== getLocale()) setLocale(event.detail.value as Locale); }} options={[{value:"en-IN",label:"English"},{value:"hi-IN",label:"हिन्दी"}]}/>;
}
