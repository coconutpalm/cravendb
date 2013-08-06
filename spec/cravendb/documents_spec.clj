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
