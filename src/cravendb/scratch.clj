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
  (use [cravendb.testing]
       [cravendb.core]
       [clojure.tools.logging :only (info debug error)] 
       [clojure.pprint]))

#_ (indexengine/indexes-which-are-up-to-date 
     {
       :chasers '({:id "one"})
       :compiled-indexes '({:id "one"} {:id "two"})
    })

#_ (with-open [db (storage/create-storage "testdb")]
     (with-open [tx (.ensure-transaction db)]
       (.commit! (.store tx (str indexing/last-indexed-etag-key) (integer-to-etag 0))))

     (with-open [tx (.ensure-transaction db)] 
       (indexengine/needs-a-new-chaser {
          :db tx
          :chasers ()
        }
        {
          :id "blah" 
        })))

;; Ensure that we're setting the etag on each index after a process
#_ (do
     (with-open [db (storage/create-storage "testdb")
                 ie (indexengine/create-engine db) ]
    (try
      (.start ie)

      (with-open [tx (.ensure-transaction db)]
        (-> tx
          (docs/store-document "1" (pr-str { :foo "bar" }) ) 
          (docs/store-document "2" (pr-str { :foo "bas" }) ) 
          (docs/store-document "3" (pr-str { :foo "baz" }) )
          (.commit!)))

    (with-open [tx (.ensure-transaction db)]
      (-> tx
          (indexes/put-index { :id "by_bar" :map "(fn [doc] {\"foo\" (:foo doc)})"})   
          (.commit!))) 
      
    (indexing/wait-for-index-catch-up db 1)

    (println (with-open [tx (.ensure-transaction db)]
      (indexes/get-last-indexed-etag-for-index tx "by_bar")))

      (finally (.stop ie))))
      (fs/delete-dir "testdb")) 

#_ (do
     (with-open [db (storage/create-storage "testdb")
                 ie (indexengine/create-engine db) ]
    (try
      (.start ie)
      (with-open [tx (.ensure-transaction db)]
        (-> tx
          (docs/store-document "1" (pr-str { :foo "bar" }) ) 
          (docs/store-document "2" (pr-str { :foo "bas" }) ) 
          (docs/store-document "3" (pr-str { :foo "baz" }) )
          (.commit!)))

    (with-open [tx (.ensure-transaction db)]
      (-> tx
        (indexes/put-index { :id "by_bar" :map "(fn [doc] {\"foo\" (:foo doc)})"})   
        (.commit!))) 
      
    (indexing/wait-for-index-catch-up db 1)
  
    (with-open [tx (.ensure-transaction db)]
      (-> tx
        (indexes/put-index { :id "by_foo" :map "(fn [doc] {\"foo\" (:foo doc)})"})   
        (.commit!))) 

    (println (query/execute db ie { :index "by_foo" :query "foo:bar" :wait true :wait_duration 1} )) 

    (finally
      (.stop ie))))
    (fs/delete-dir "testdb"))

#_ (client/put-index 
    "http://localhost:8080" 
    "by_username" 
    "(fn [doc] {\"username\" (doc :username)})")

#_ (client/put-document 
          "http://localhost:8080" 
          "2" { :username "bob"})

#_ (client/query 
          "http://localhost:8080" 
          { :query "username:bob" :index "by_username" :wait true})

#_ (client/delete-document "http://localhost:8080" "2" )

#_ (doall '(nil nil nil))


#_ (def index (lucene/create-memory-index))
#_ (def wirter (.open-writer index))
#_ (map #(println %1) (.getMethods (type (:writer wirter))))


#_ (.close wirter)
#_ (.close index)
#_ (.delete-all-entries-for wirter "1")
