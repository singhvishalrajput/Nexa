package com.nexa.api.service;
import com.nexa.api.beans.Account;


import java.util.*;

/** Local hashed bag of words, normalized for cosine search. No external model or network. */
public class BasicEmbeddingProvider implements EmbeddingProvider {
  private static final Set<String> STOP =
      Set.of(
          "show", "me", "my", "the", "a", "an", "what", "is", "are", "do", "i", "have", "please",
          "tell", "list", "check", "read", "can", "you", "of", "to", "for", "which", "did", "how",
          "much", "few", "from");

  public double[] embed(String text) {
    double[] vector = new double[2048];
    for (String token : text.toLowerCase(Locale.ROOT).replace("'", "").split("[^a-z0-9]+")) {
      if (token.isBlank() || STOP.contains(token) || token.matches("[0-9]+")) continue;
      token =
          switch (token) {
            case "payments", "payment" -> "payment";
            case "transactions", "transaction" -> "transaction";
            case "recently", "latest", "last" -> "recent";
            case "mandates", "autopay", "subscriptions", "subscription" -> "mandate";
            case "bills" -> "bill";
            case "cards" -> "card";
            case "accounts" -> "account";
            case "balances", "money" -> "balance";
            case "beneficiaries", "payees", "payee" -> "beneficiary";
            case "loans", "emi", "emis" -> "loan";
            case "savings" -> "saving";
            default -> token;
          };
      vector[Math.floorMod(token.hashCode(), vector.length)] += 1;
    }
    double norm = Math.sqrt(Arrays.stream(vector).map(v -> v * v).sum());
    if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    return vector;
  }
}
