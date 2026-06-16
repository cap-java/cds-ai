/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.recommendation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sap.cds.CdsData;
import com.sap.cds.feature.aicore.api.AICoreService;
import com.sap.cds.feature.recommendation.api.RecommendationClient;
import com.sap.cds.feature.recommendation.api.RecommendationClientResolver;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.utils.DraftUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
class FioriRecommendationHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(FioriRecommendationHandler.class);
  private static final int DEFAULT_CONTEXT_ROW_LIMIT = 2000;

  private final AICoreService aiCoreService;
  private final RecommendationClientResolver clientResolver;
  private final RecommendationResultParser resultParser = new RecommendationResultParser();
  // Avoids re-evaluating the CDS model on every read to check whether an entity has prediction
  // columns. Keys are "<tenantId>:<entityName>" because if an entity needs a prediction can be
  // different across tenants.
  private final Cache<String, Boolean> entitiesWithoutPredictionsPerTenant =
      Caffeine.newBuilder().maximumSize(10_000).build();

  FioriRecommendationHandler(
      AICoreService aiCoreService, RecommendationClientResolver clientResolver) {
    this.aiCoreService = aiCoreService;
    this.clientResolver = clientResolver;
  }

  void invalidateTenant(String tenantId) {
    String prefix = tenantKey(tenantId) + ":";
    entitiesWithoutPredictionsPerTenant.asMap().keySet().removeIf(k -> k.startsWith(prefix));
  }

  private static String tenantKey(String tenantId) {
    return tenantId != null ? tenantId : "";
  }

  @After(entity = "*")
  public void afterRead(CdsReadEventContext context, List<CdsData> dataList) {
    CdsStructuredType target = context.getTarget();
    if (target == null) {
      return;
    }
    String tenantId = context.getUserInfo().getTenant();
    String entityName = target.getQualifiedName();
    String cacheKey = tenantKey(tenantId) + ":" + entityName;
    if (entitiesWithoutPredictionsPerTenant.getIfPresent(cacheKey) != null) {
      return;
    }

    if (dataList.size() != 1) {
      return;
    }

    CdsData row = dataList.get(0);

    if (!DraftUtils.isDraftEnabled(target)) {
      return;
    }

    if (!Boolean.FALSE.equals(row.get(Drafts.IS_ACTIVE_ENTITY))) {
      return;
    }

    // rowType reflects the projected shape (columns actually selected); target is the full entity.
    // Fall back to target when rowType is absent, e.g. when the result carries no type metadata.
    CdsStructuredType rowType = context.getResult().rowType();
    if (rowType == null) {
      rowType = target;
    }

    int limit =
        context
            .getCdsRuntime()
            .getEnvironment()
            .getProperty(
                "cds.ai.recommendations.contextRowLimit", Integer.class, DEFAULT_CONTEXT_ROW_LIMIT);

    var builder = new RecommendationContextBuilder(target, rowType, limit);

    if (builder.predictionElementNames().isEmpty()) {
      entitiesWithoutPredictionsPerTenant.put(cacheKey, Boolean.TRUE);
      return;
    }

    if (builder.contextColumns().isEmpty()) {
      logger.debug("No suitable context columns found, skipping predictions.");
      return;
    }

    PersistenceService db =
        context
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);
    List<CdsData> contextRows = new ArrayList<>(db.run(builder.buildContextQuery()).list());
    if (contextRows.size() < 2) {
      logger.debug("Not enough context rows (minimum 2), skipping predictions.");
      return;
    }

    CdsData predictRow = builder.buildPredictRow(row);
    if (predictRow == null) {
      logger.debug("Current row already has values for all prediction columns, skipping.");
      return;
    }

    RecommendationClient client = clientResolver.resolve(aiCoreService);
    List<CdsData> predictions =
        client.predict(
            predictRow, contextRows, builder.predictionElementNames(), builder.keyNames());

    if (predictions.isEmpty()) {
      logger.warn("No predictions returned from AI client.");
      return;
    }
    if (predictions.size() > 1) {
      logger.warn("Multiple predictions returned from AI client, but only one was expected.");
      return;
    }

    List<String> missingPredictionElementNames =
        builder.predictionElementNames().stream().filter(c -> row.get(c) == null).toList();
    Map<String, Object> recommendations =
        resultParser.buildRecommendations(
            db, predictions.get(0), missingPredictionElementNames, context, rowType);
    row.put("SAP_Recommendations", recommendations);
  }
}
