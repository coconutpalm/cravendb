(ns cravendb.database
  (:require [cravendb.storage :as s]
            [cravendb.indexing :as indexing] 
            [cravendb.query :as query] 
            [cravendb.indexstore :as indexes] 
            [cravendb.indexengine :as indexengine] 
            [cravendb.documents :as docs]
            [clojure.tools.logging :refer [info error debug]]))

(defrecord Database [storage index-engine]
  java.io.Closeable
  (close [this] 
    (indexengine/stop index-engine)
    (.close index-engine)
    (.close storage)))

(def last-etag-key "last-etag")
(defn last-etag-in
  [storage]
  (or (s/get-string storage last-etag-key) (zero-etag)) )

(defn create
  [path]
  (let [storage (s/create-storage path)
        index-engine (indexengine/create-engine storage)]
    (indexengine/start index-engine)
    (assoc (Database. storage index-engine)
           :last-etag (last-etag-in storage))))

(defn interpret-bulk-operation [tx op]
  (case (:operation op)
    :docs-delete (docs/delete-document tx (:id op))
    :docs-put (docs/store-document 
                tx (:id op) (pr-str (:document op)) (next-etag tx))))

(defn query
  [{:keys [storage index-engine]} params]
  (debug "Querying for " params)
  (query/execute storage index-engine params))

(defn next-etag
  [{:keys [last-etag]}]
    (integer-to-etag (swap! last-etag inc)))

(defn put-document 
  [{:keys [storage] :as db} id document]
  (debug "putting a document:" id document)
  (with-open [tx (s/ensure-transaction storage)]
    (s/commit! (docs/store-document tx id document (next-etag db)))))

(defn delete-document 
  [{:keys [storage]} id]
  (debug "deleting a document with id " id)
  (with-open [tx (s/ensure-transaction storage)]
    (s/commit! (docs/delete-document tx id))))

(defn load-document 
  [{:keys [storage]} id]
  (debug "getting a document with id " id)
  (docs/load-document storage id))

(defn bulk 
  [{:keys [storage]} operations]
  (debug "Bulk operation: ")
  (with-open [tx (s/ensure-transaction storage)]
    (s/commit! 
      (reduce
        interpret-bulk-operation
        tx
        operations))))

(defn put-index 
  [{:keys [storage]} index]
  (debug "putting an in index:" index)
  (with-open [tx (s/ensure-transaction storage)]
    (s/commit! (indexes/put-index tx index))))

(defn delete-index 
  [{:keys [storage]} id]
  (debug "deleting an index" id)
  (with-open [tx (s/ensure-transaction storage)]
    (s/commit! (indexes/delete-index tx id))))

(defn load-index 
  [{:keys [storage]} id]
  (debug "getting an index with id " id)
  (with-open [tx (s/ensure-transaction storage)]
    (indexes/load-index tx id)))
  

