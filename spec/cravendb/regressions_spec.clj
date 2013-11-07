(ns cravendb.regressions-spec
  (:use [speclj.core]
        [cravendb.core] 
        [cravendb.testing])
  (:require [cravendb.indexing :as indexing]
            [cravendb.documents :as docs]
            [cravendb.indexstore :as indexes]
            [cravendb.indexengine :as indexengine]
            [cravendb.storage :as s]
            [cravendb.query :as query]
            [cravendb.client :as client]
            [cravendb.database :as database]
            [cravendb.lucene :as lucene]))

;(describe "Chaser not able to complete because the last doc cannot index"
;  (it "will actually catch up"
;      (with-db (fn [db]
;
;        (with-open [tx (s/ensure-transaction db)]
;          (s/commit! (indexes/put-index tx 
;            { :id "test" :map "(fn [doc] nil)"} {:synctag (integer-to-synctag 1)})))
;
;        (with-open [tx (s/ensure-transaction db)]
;          (-> tx
;            (docs/store-document "1" { :fod "bar" } {:synctag (integer-to-synctag 2)}) 
;            (s/commit!)))
;
;        (with-open [ie (indexengine/create-engine db)]
;          (indexing/index-documents! db (indexengine/compiled-indexes ie)))
;
;        (should= (integer-to-synctag 2) 
;                 (indexes/get-last-indexed-synctag-for-index db "test")))))) 

(def test-index 
  "(fn [doc] (if (:whatever doc) { \"whatever\" (:whatever doc) } nil ))")

(defn add-by-whatever-index [instance]
  (database/put-index instance { 
        :id "by_whatever" 
        :map test-index}))

(describe "Querying a newly created index"
  (it "will not fall over clutching a bottle of whisky"
    (with-full-setup
    (fn [instance]
      (add-by-whatever-index instance) 
      (should-not-throw 
        (database/query instance
          { :query "*" :sort-order :desc :sort-by "whatever" :index "by_whatever"}))))))
 
