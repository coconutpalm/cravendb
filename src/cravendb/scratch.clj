(ns cravendb.scratch
  "The sole purpose of this file is to act as a place to play with stuff in repl"
  (:require [cravendb.indexing :as indexing]
            [cravendb.documents :as docs]
            [cravendb.client :as client]
            [cravendb.query :as query]
            [cravendb.indexstore :as indexes]
            [cravendb.indexengine :as indexengine]
            [cravendb.storage :as storage]
            [me.raynes.fs :as fs]
            [ring.adapter.jetty :refer [run-jetty]]
            [cravendb.http :as http]  
            [cravendb.lucene :as lucene])
  (use [cravendb.testing]))

#_ (with-test-server 
      (fn []
        (client/put-index 
          "http://localhost:9000" 
          "by_username" 
          "(fn [doc] {\"username\" (doc :username)})")
        (client/put-document 
          "http://localhost:9000" 
          "1" { :username "bob"})
        (client/put-document 
          "http://localhost:9000" 
          "1" { :username "alice"})
        (client/put-document 
          "http://localhost:9000" 
          "1" { :username "alice"})
        (client/put-document 
          "http://localhost:9000" 
          "1" { :username "alice"})
        (client/put-document 
          "http://localhost:9000" 
          "1" { :username "alice"})
        (client/put-document 
          "http://localhost:9000" 
          "1" { :username "alice"})
        (client/put-document 
          "http://localhost:9000" 
          "4" { :username "alice"})
       (println (client/query 
          "http://localhost:9000" 
          { :query "username:alice" :index "by_username" :wait true}))))  

#_ (client/put-index 
    "http://localhost:8080" 
    "by_username" 
    "(fn [doc] {\"username\" (doc :username)})")

#_ (client/put-document 
          "http://localhost:8080" 
          "1" { :username "alice"})
