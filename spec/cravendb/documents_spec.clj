(ns cravendb.documents-spec
  (:use [speclj.core]
        [cravendb.testing]))

(describe "Various db operations"
  (it "can put and get a document"
    (with-db (fn [db]
      (.-put db "1" "hello")
      (should (= (.-get db "1") "hello")))))
  (it "returns nil for a non-existent document"
    (with-db (fn [db]
      (should (= (.-get db "1337") nil))
      (.close db))))
  (it "can delete a document"
    (with-db (fn [db]
      (.-put db "1" "hello")
      (.-delete db "1")
      (should (= (.-get db "1") nil))
      (.close db)))))

(describe "Etags"
  (it "will have an etag starting at zero before anything is written"
    (with-db (fn [db]
      (should= (.last-etag db) 0))))
  (it "Will have an etag greater than zero after committing a single document"
    (with-db (fn [db]
      (.-put db "1" "hello")
      (should (> (.last-etag db) 0)))))
  (it "links an etag with a document upon writing"
    (with-db (fn [db]
      (.-put db "1" "hello")
      (should (> (.get-etag db "1") 0)))))
  (it "can retrieve documents written since an etag"
    (with-db (fn [db]
      (.-put db "1" "hello")
      (let [etag (.last-etag db)]
        (.-put db "2" "hello")
        (.-put db "3" "hello")
        (should== '("2" "3") (.written-since-etag db etag)))))))
