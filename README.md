[![Build Status](https://travis-ci.org/robashton/CravenDB.png?branch=master)](https://travis-ci.org/robashton/CravenDB)

# CravenDB

- A document database written in Clojure for learning purposes
- It is based loosely on RavenDB's design
- A rough to-do can be found in the file "todo.markdown"
- Most likely we're talking
  - Vector clocks to ease conflcits
  - MVCC by default
  - Master/Master replication by default
  - Dynamic queries over un-indexed documents
  - Distributed joins across indexed documents

# Instructions

- Use the repl to explore
- Use the tests to verify (lein specs)
- Run the http server with 'lein run'


# License

Licensed under the EPL (see the file epl.html)
