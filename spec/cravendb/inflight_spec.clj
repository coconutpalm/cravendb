(ns cravendb.inflight-spec
  (:use [speclj.core]
        [cravendb.testing]
        [cravendb.core])

  (:require
    [cravendb.documents :as docs]
    [cravendb.storage :as s]
    [cravendb.inflight :as inflight]
    [me.raynes.fs :as fs]
    ))

(describe 
  "In-flight transactions"
  (with db (s/create-in-memory-storage))
  (with te (inflight/create @db "root"))
  (after 
    (.close @db)
    (fs/delete-dir "testdir"))

  (describe "Adding a single document via the in-flight system"
    (with txid (inflight/open @te))
    (before
      (inflight/add-document @te @txid "doc-1" {:foo "bar"} {})
      (inflight/complete! @te @txid))
    (it "will write the document to storage"
      (should (docs/load-document @db "doc-1")))
    (it "will clear the document from the in-flight system"
      (should-not (inflight/is-registered? @te "doc-1")))
    (it "will clear the transaction from the in-flight system"
       (should-not (inflight/is-txid? @te @txid))))

  (describe "Deleting a single document via the in-flight system"
    (with txid (inflight/open @te))
    (before
      (with-open [tx (s/ensure-transaction @db)] 
        (s/commit! (docs/store-document tx "doc-1" {} {}))
      (inflight/delete-document @te @txid "doc-1" {})
      (inflight/complete! @te @txid)))
    (it "will write the document to storage"
      (should-not (docs/load-document @db "doc-1")))
    (it "will clear the document from the in-flight system"
      (should-not (inflight/is-registered? @te "doc-1")))
    (it "will clear the transaction from the in-flight system"
       (should-not (inflight/is-txid? @te @txid))))
  
  (describe "Two clients writing at the same time without specifying history"
    (with txid-1 (inflight/open @te))
    (with txid-2 (inflight/open @te))
    (before
      (inflight/add-document @te @txid-1 "doc-1" { :name "1"} {})
      (inflight/add-document @te @txid-2 "doc-1" { :name "2"} {})
      (inflight/complete! @te @txid-2)
      (inflight/complete! @te @txid-1))

      (it "will write the second document (last-write-wins)"
        (should== {:name "2"} (docs/load-document @db "doc-1")))

      (it "will not generate a conflict"
        (should= 0 (count (docs/conflicts @db)))))


  (describe "Deleting a conflicting document via the in-flight system"
            
            )

  (describe "Two clients adding a new document simultaneously"
            
            )

  (describe "Two clients modifying an existing simultaneously"
            
            )

  (describe "Two clients writing a new document consecutively"
            
            )
  (describe "A client deleting a document then another client adding a document"
            
            ))





