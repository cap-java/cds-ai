using {itest.mt} from '../db/schema';
using from 'com.sap.cds/ai';

service MtTestService {
  entity Products as projection on mt.Products;
}
