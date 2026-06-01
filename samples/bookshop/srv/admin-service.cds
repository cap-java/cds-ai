using {sap.capire.bookshop as my} from '../db/schema';
service AdminService @(requires: 'any') {
  entity Books   as projection on my.Books;
  entity Authors as projection on my.Authors;
}

annotate AdminService.Books with {
  genre @Common.Text: genre.name;
}
