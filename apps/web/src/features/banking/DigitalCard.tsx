import { SensitiveNumber } from "./SensitiveNumber";
import { Product } from "./api";
import { humanize } from "../../services/banking-content";

export function DigitalCard({ product }: { product: Product }) {
    return <div class="bank-digital-card">
        <img src={product.cardType === "CREDIT" ? "styles/images/nexa-credit-digital.png" : "styles/images/nexa-debit-digital.png"} alt="" width="1584" height="1000" decoding="async"/>
        <div class="bank-digital-card-top"><span>Nexa</span><span>{product.cardType ? humanize(product.cardType) : "Digital card"}</span></div>
        {product.numberMasked && <span class="bank-digital-card-number"><SensitiveNumber kind="cards" id={product.id} masked={product.numberMasked}/></span>}
    </div>;
}
