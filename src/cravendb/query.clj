(ns cravendb.query
  (:use [cravendb.core]
       [cravendb.indexstore :as indexes]
       [clojure.tools.logging :only (info error debug)])
  (:require    
    [cravendb.indexing :as indexing]
    [cravendb.indexengine :as indexengine]
    [cravendb.documents :as docs]
    [cravendb.lucene :as lucene]
    [cravendb.storage :as s]))

(defn convert-results-to-documents [tx results]
  (filter boolean (map (partial docs/load-document tx) results)))

(defn lucene-producer [tx reader opts]
  (fn [offset amount]
    (convert-results-to-documents tx
      (drop offset (lucene/query 
                     reader 
                     (:query opts) 
                     (+ offset amount) 
                     (:sort-by opts) 
                     (:sort-order opts))))))
(defn lucene-page 
  ([producer page-size] (lucene-page producer 0 page-size))
  ([producer current-offset page-size]
   {
    :results (producer current-offset page-size)
    :next (fn [] (lucene-page producer (+ current-offset page-size) page-size))
   }))

(defn lucene-seq 
  ([page] (lucene-seq page ()))
  ([page coll] (lucene-seq page (:results page) coll))
  ([page src coll]
   (cond
     (empty? (:results page)) coll
     (empty? src) (lucene-seq ((:next page)) coll)
     :else (cons (first src) (lazy-seq (lucene-seq page (rest src) coll))))))

(declare execute)

(defn query-with-storage [db storage {:keys [offset amount] :as opts}]
  (try
    (with-open [reader (lucene/open-reader storage)
              tx (s/ensure-transaction db)]
      (doall (take amount (drop offset 
        (lucene-seq 
         (lucene-page (lucene-producer tx reader opts) (+ offset amount)))))))

    (catch Exception ex ;; TODO: Be more specific
      (info "Failed to query with" opts "because" ex)
      ())))

(defn wait-for-new-index [db index-engine query]
  (execute db index-engine (assoc query :wait 5)))

(defn query-without-storage [db index-engine query]
  (if (indexes/load-index db (:index query))
      (wait-for-new-index db index-engine query)
      nil))

(defn execute [db index-engine {:keys [index wait wait-duration] :as opts}]
  (if wait 
    (indexing/wait-for-index-catch-up db index wait-duration))
  (let [storage (indexengine/get-index-storage index-engine index)]
    (if storage 
      (query-with-storage db storage opts)
      (query-without-storage db index-engine opts))))
