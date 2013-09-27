# Implemented thus far

- Transactional key-value storage on top of LevelDB
- Document storage on top of that
- Index storage on top of documents
- Etags per documents kept per version
- Wrapper around Lucene for index content
- Indexing process that executes 'maps' documents into Lucene
- Querying against the indexes, loading documents that match
- HTTP API around the above
- Basic client API around the HTTP API
- Bulk imports
- A rudimentary client-side session helper
- Paging through query results

# Pending/debt/etc

### Immediate priority

- Perform indexing in chunks (at the moment it's all or nothing)
- Allow sort orders to be specified
- Indexing errors
  - Can get an error if there is no indexing happening for a while and there have been no indexes but lots of documents
  - Querying a newly created index can result in an error as the reader isn't open yet
  - Getting all sorts of random errors during indexing large amounts of docs that need clearing

### Can wait

- Allow restricting indexing to documents with a prefix (cats-/dogs-)
- Modification of an index needs to mean re-indexing
  - Can I get away with renaming the folder to 'to_delete-blah'
  - Then deleting it?
  - This won't work for scheduled data, need indirection there at least
- Options for Lucene
- Decide on how to expose Lucene queries to the consumer
- Process to remove deleted documents from index
- Some form of concurrency check over transactions (MVCC most likely)
- Handle errors during indexing so it doesn't infini-loop
- The index engine shouldn't be swallowing agent exceptions
- Client should be handling HTTP results properly
- HTTP server should be sending back appropriate HTTP responses
- Documents should be validated as valid clojure objects by HTTP API
- HTTP API should be validating input
- Document storage should be responsible for serializing to string
- Allow indexes to be provided as actual functions (sfn macro) - this will make testing easier

