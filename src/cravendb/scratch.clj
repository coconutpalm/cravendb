(ns cravendb.scratch
  "The sole purpose of this file is to act as a place to play with stuff in repl"
  (:use [cravendb.testing]
        [cravendb.core]
        [clojure.data.codec.base64])
  (:require [cravendb.vclock :as v]
            [cravendb.documents :as docs]
            [cravendb.http :as http]
            [clojurewerkz.vclock.core :as vclock]            
            [org.httpkit.server :refer [run-server]]
            [clojure.edn :as edn]
            [cravendb.database :as db]
            [cravendb.storage :as s]
            [me.raynes.fs :as fs]
            [cravendb.client :as client]
            [cravendb.replication :as r]
            [clojure.pprint :refer [pprint]]))


;; Bulk operations need to check history for conflicts
;; Replication needs to check history for conflicts
;; 

#_ (def tx (assoc (s/ensure-transaction (:storage instance))
              :e-id "root-1" 
               :base-vclock (:base-vclock instance)
               :last-synctag (:last-synctag instance)
               :server-id "root"))

#_ (pprint (r/replicate-into tx [
                      { :id "doc-1" :doc { :foo "bar" } :metadata { :synctag (integer-to-synctag 0)}}
                      { :id "doc-2" :doc { :foo "bar" } :metadata { :synctag (integer-to-synctag 1)}}
                      { :id "doc-3" :doc { :foo "bar" } :metadata { :synctag (integer-to-synctag 2)}}
                      { :id "doc-4" :doc { :foo "bar" } :metadata { :synctag (integer-to-synctag 3)}}
                      { :id "doc-5" :doc { :foo "bar" } :metadata { :synctag (integer-to-synctag 4)}}
                      ]))


;; Conflict scenarios
;; Document doesn't exist yet -> write away!
#_ (r/conflict-status
  nil (v/next "1" (v/new)))

;; Document exists, history is in the past -> write away!
#_ (r/conflict-status
  (v/next "1" (v/new)) (v/next "1" (v/next "1" (v/new))))

;; Document exists, history is in the future -> drop it!
#_ (r/conflict-status
    (v/next "1" (v/next "1" (v/new))) (v/next "1" (v/new)))

;; Document exists, history is the same -> drop it
#_ (r/conflict-status
     (v/next "1" (v/new)) (v/next "1" (v/new)))

;; Document exists, history is conflicting -> conflict
#_ (r/conflict-status
    (v/next "1" (v/next "1" (v/new))) (v/next "2" (v/next "1" (v/new))))

;; If there is no document specified, it's a delete
;; If there is a document specifeid, it's a write
#_ (r/action-for { :doc "blah"})
#_ (r/action-for {})

;; Okay, so if conflict we should write a conflict
;; If write, we should the write the document
;; If skip, we should return the un-modified 

;; Can I replicate here without too much faff?
;; Oh, I've done most of the code but I'm not assigning new synctags on write
;; I'll need to do that if I want indexing to work on written servers
;; or the daisy chaining of replication destinations

(defn start []
  (def source (db/create "testdb_source"))
  (def dest (db/create "testdb_dest"))
  (def server-source (run-server (http/create-http-server source) { :port 8081}))
  (def server-dest (run-server (http/create-http-server source) {:port 8082})))

(defn stop []
  (server-source)
  (server-dest)
  (.close source)
  (.close dest)
  (fs/delete-dir "testdb_source")
  (fs/delete-dir "testdb_dest") )

(defn restart []
  (stop)
  (start))

#_ (start)
#_ (stop)
#_ (restart)


;; So theoretically if I run these then there should be no conflicts
;; because replication would skip un-recognised documents
;; Synctags will be screwed though
#_ (r/pump-replication (:storage dest) "http://localhost:8081")
#_ (r/pump-replication (:storage source) "http://localhost:8082")
#_ (r/last-replicated-synctag (:storage dest) "http://localhost:8081")
#_ (r/last-replicated-synctag (:storage source) "http://localhost:8082")
#_ (docs/conflicts (:storage dest))
#_ (docs/conflicts (:storage source))

;; So, let's stick a document in source, get it into dest, then run the replication to prove no side effects
(db/put-document source "doc-1" { :foo "bar"})
;; Great, two way replication seemed fairly sound

;; How about sticking two different documents, one in each server
(db/put-document source "doc-2" { :foo "bar"})
(db/put-document dest "doc-3" { :foo "bar"})


;; No conflicts, and the documents are in both servers?
(db/load-document source "doc-3")
(db/load-document dest "doc-2")

;; They're not in both servers because we're not getting new synctags when writing documents
;; via replication











