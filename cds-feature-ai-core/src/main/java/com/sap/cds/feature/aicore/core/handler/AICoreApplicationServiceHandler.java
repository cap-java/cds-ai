/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnStructuredTypeRef;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.utils.OrderConstants;

/**
 * Intercepts CRUD events on application service entities that are projections on AICore entities
 * and delegates them to the AICore service. Without this, the framework would try to forward these
 * to the PersistenceService, which fails since AICore entities have no database tables.
 */
@ServiceName(value = "*", type = ApplicationService.class)
public class AICoreApplicationServiceHandler implements EventHandler {

  private final CqnService aiCoreService;

  public AICoreApplicationServiceHandler(CqnService aiCoreService) {
    this.aiCoreService = aiCoreService;
  }

  @On
  @HandlerOrder(OrderConstants.On.FEATURE)
  public void onRead(CdsReadEventContext context) {
    String sourceEntity = resolveAICoreSource(context.getTarget(), context.getModel());
    if (sourceEntity == null) {
      return;
    }
    CqnSelect rewritten = CQL.copy(context.getCqn(), entityModifier(sourceEntity));
    context.setResult(aiCoreService.run(rewritten));
  }

  @On
  @HandlerOrder(OrderConstants.On.FEATURE)
  public void onCreate(CdsCreateEventContext context) {
    String sourceEntity = resolveAICoreSource(context.getTarget(), context.getModel());
    if (sourceEntity == null) {
      return;
    }
    context.setResult(aiCoreService.run(CQL.copy(context.getCqn(), entityModifier(sourceEntity))));
  }

  @On
  @HandlerOrder(OrderConstants.On.FEATURE)
  public void onUpdate(CdsUpdateEventContext context) {
    String sourceEntity = resolveAICoreSource(context.getTarget(), context.getModel());
    if (sourceEntity == null) {
      return;
    }
    context.setResult(aiCoreService.run(CQL.copy(context.getCqn(), entityModifier(sourceEntity))));
  }

  @On
  @HandlerOrder(OrderConstants.On.FEATURE)
  public void onDelete(CdsDeleteEventContext context) {
    String sourceEntity = resolveAICoreSource(context.getTarget(), context.getModel());
    if (sourceEntity == null) {
      return;
    }
    context.setResult(aiCoreService.run(CQL.copy(context.getCqn(), entityModifier(sourceEntity))));
  }

  private String resolveAICoreSource(CdsEntity entity, CdsModel model) {
    if (entity == null || !entity.isProjection()) {
      return null;
    }
    return entity
        .query()
        .filter(q -> q.from().isRef())
        .map(q -> CqnAnalyzer.create(model).analyze(q).targetEntity())
        .map(CdsEntity::getQualifiedName)
        .filter(name -> name.startsWith("AICore."))
        .orElse(null);
  }

  private static Modifier entityModifier(String targetEntity) {
    return new Modifier() {
      @Override
      public CqnStructuredTypeRef ref(CqnStructuredTypeRef ref) {
        var copy = CQL.copy(ref);
        copy.rootSegment().id(targetEntity);
        return copy.build();
      }
    };
  }
}
