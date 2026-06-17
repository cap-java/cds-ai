/*
 * © 2026 SAP SE or an SAP affiliate company and cds-ai contributors.
 */
package com.sap.cds.feature.aicore.core.handler;

import com.sap.cds.CdsData;
import com.sap.cds.feature.aicore.api.AICore;
import com.sap.cds.ql.cqn.AnalysisResult;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnDelete;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ServiceName(AICore.SERVICE_NAME)
public class MockEntityHandler implements EventHandler {

  private final Map<String, Map<String, Object>> resourceGroups = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> deployments = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> configurations = new ConcurrentHashMap<>();

  // --- Resource Groups ---

  @On(entity = AICore.RESOURCE_GROUPS)
  public void readResourceGroups(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    AnalysisResult analysis = analyzer.analyze(select);
    Map<String, Object> keys = analysis.targetKeys();

    String id = (String) keys.get("resourceGroupId");
    if (id != null) {
      Map<String, Object> rg = resourceGroups.get(id);
      context.setResult(rg != null ? List.of(rg) : List.of());
    } else {
      Map<String, Object> values = analysis.targetValues();
      String tenantId = (String) values.get("tenantId");
      if (tenantId != null) {
        List<Map<String, Object>> filtered =
            resourceGroups.values().stream()
                .filter(rg -> tenantId.equals(rg.get("tenantId")))
                .toList();
        context.setResult(filtered);
      } else {
        context.setResult(List.copyOf(resourceGroups.values()));
      }
    }
  }

  @On(entity = AICore.RESOURCE_GROUPS)
  public void createResourceGroups(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> entry : insert.entries()) {
      String id = (String) entry.getOrDefault("resourceGroupId", UUID.randomUUID().toString());
      CdsData stored = CdsData.create(entry);
      stored.put("resourceGroupId", id);
      stored.put("status", "PROVISIONED");
      resourceGroups.put(id, stored);
      results.add(stored);
    }
    context.setResult(results);
  }

  @On(entity = AICore.RESOURCE_GROUPS)
  public void updateResourceGroups(CdsUpdateEventContext context) {
    CqnUpdate update = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();
    String id = (String) keys.get("resourceGroupId");
    Map<String, Object> existing = resourceGroups.getOrDefault(id, CdsData.create());
    for (Map<String, Object> entry : update.entries()) {
      existing.putAll(entry);
    }
    existing.put("resourceGroupId", id);
    resourceGroups.put(id, existing);
    context.setResult(List.of(CdsData.create(existing)));
  }

  @On(entity = AICore.RESOURCE_GROUPS)
  public void deleteResourceGroups(CdsDeleteEventContext context) {
    CqnDelete delete = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(delete).targetKeys();
    String id = (String) keys.get("resourceGroupId");
    resourceGroups.remove(id);
    context.setResult(List.of());
  }

  // --- Deployments ---

  @On(entity = AICore.DEPLOYMENTS)
  public void readDeployments(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(select).targetKeys();

    String id = (String) keys.get("id");
    if (id != null) {
      Map<String, Object> d = deployments.get(id);
      context.setResult(d != null ? List.of(d) : List.of());
    } else {
      context.setResult(List.copyOf(deployments.values()));
    }
  }

  @On(entity = AICore.DEPLOYMENTS)
  public void createDeployments(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> entry : insert.entries()) {
      String id = (String) entry.getOrDefault("id", UUID.randomUUID().toString());
      CdsData stored = CdsData.create(entry);
      stored.put("id", id);
      stored.put("status", "RUNNING");
      deployments.put(id, stored);
      results.add(stored);
    }
    context.setResult(results);
  }

  @On(entity = AICore.DEPLOYMENTS)
  public void updateDeployments(CdsUpdateEventContext context) {
    CqnUpdate update = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(update).targetKeys();
    String id = (String) keys.get("id");
    Map<String, Object> existing = deployments.getOrDefault(id, CdsData.create());
    for (Map<String, Object> entry : update.entries()) {
      existing.putAll(entry);
    }
    existing.put("id", id);
    deployments.put(id, existing);
    context.setResult(List.of(CdsData.create(existing)));
  }

  @On(entity = AICore.DEPLOYMENTS)
  public void deleteDeployments(CdsDeleteEventContext context) {
    CqnDelete delete = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(delete).targetKeys();
    String id = (String) keys.get("id");
    deployments.remove(id);
    context.setResult(List.of());
  }

  // --- Configurations ---

  @On(entity = AICore.CONFIGURATIONS)
  public void readConfigurations(CdsReadEventContext context) {
    CqnSelect select = context.getCqn();
    CdsModel model = context.getModel();
    CqnAnalyzer analyzer = CqnAnalyzer.create(model);
    Map<String, Object> keys = analyzer.analyze(select).targetKeys();

    String id = (String) keys.get("id");
    if (id != null) {
      Map<String, Object> c = configurations.get(id);
      context.setResult(c != null ? List.of(c) : List.of());
    } else {
      context.setResult(List.copyOf(configurations.values()));
    }
  }

  @On(entity = AICore.CONFIGURATIONS)
  public void createConfigurations(CdsCreateEventContext context) {
    CqnInsert insert = context.getCqn();
    List<Map<String, Object>> results = new ArrayList<>();
    for (Map<String, Object> entry : insert.entries()) {
      String id = (String) entry.getOrDefault("id", UUID.randomUUID().toString());
      CdsData stored = CdsData.create(entry);
      stored.put("id", id);
      configurations.put(id, stored);
      results.add(stored);
    }
    context.setResult(results);
  }
}
