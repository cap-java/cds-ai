using {itest} from '../db/schema';
using { AICore } from 'com.sap.cds/ai';

service TestService {
  entity Products       as projection on itest.Products;
  entity Configurations as projection on AICore.configurations;
  entity Deployments    as projection on AICore.deployments;
  entity ResourceGroups as projection on AICore.resourceGroups;
}

service RecommendationTestService @(requires: 'any') {
  @odata.draft.enabled
  entity Tasks as projection on itest.Tasks;

  @odata.draft.enabled
  entity BooksWithCustomKey as projection on itest.BooksWithCustomKey;

  @odata.draft.enabled
  entity OrderItems as projection on itest.OrderItems;

  @readonly entity Categories as projection on itest.Categories;
  @readonly entity Priorities as projection on itest.Priorities;
}

annotate RecommendationTestService.Tasks with {
  category @Common.ValueListWithFixedValues;
  priority @Common.ValueListWithFixedValues;
}

annotate RecommendationTestService.Tasks with {
  category @Common.Text: category.name;
  priority @Common.Text: priority.name;
}

annotate RecommendationTestService.BooksWithCustomKey with {
  category @Common.ValueListWithFixedValues;
}

annotate RecommendationTestService.OrderItems with {
  category @Common.ValueListWithFixedValues;
}
