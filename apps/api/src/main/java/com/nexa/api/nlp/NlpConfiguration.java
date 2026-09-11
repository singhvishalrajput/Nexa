package com.nexa.api.nlp;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class NlpConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public EmbeddingProvider embeddingProvider() {
    return new BasicEmbeddingProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public IntentRepository intentRepository() {
    return new IntentRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public VectorIndex vectorIndex(IntentRepository repository, EmbeddingProvider provider) {
    return new InMemoryVectorIndex(repository, provider);
  }

  @Bean
  @ConditionalOnMissingBean
  public IntentClassifier intentClassifier(
      EmbeddingProvider provider,
      VectorIndex index,
      @Value("${NLP_INTENT_THRESHOLD:0.70}") double threshold) {
    return new BasicIntentClassifier(provider, index, threshold);
  }

  @Bean
  @ConditionalOnMissingBean
  public EntityExtractor entityExtractor() {
    return new BasicEntityExtractor(Clock.systemUTC());
  }
}
