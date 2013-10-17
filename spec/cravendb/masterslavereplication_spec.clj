(ns cravendb.masterslavereplication-spec
  (:require [cravendb.database :as db]
            [cravendb.client :as c]
            [cravendb.testing :refer [start-server stop-server]]
            [cravendb.replication :as replication]
            [cravendb.documents :as docs])

  (:use [speclj.core]))

(defn insert-2000-documents [instance] 
  (db/bulk instance
      (map (fn [i]
      {
        :operation :docs-put
        :id (str "docs-" i)
        :document { :whatever (str "Trolololol" i)} 
        }) (range 0 2000)))) 

(describe "Getting a stream of documents from a source server"
  (with-all master (start-server))
  (before-all 
    (insert-2000-documents (:instance @master)))
  (after-all (stop-server @master))

  (it "will stream all of the documents"
    (should= 2000 (count (c/stream-seq (:url @master)))))

  (it "will start a page from the next etag specified"
    (let [stream (c/stream-seq (:url @master))
          first-etag (get-in (first stream) [:metadata :etag])
          second-etag (get-in (second stream) [:metadata :etag])]
      (should= second-etag
        (get-in (first (c/stream-seq (:url @master) first-etag)) [:metadata :etag])) ))) 



(describe 
  "Various master/slave scenarios"
    (with master (start-server 8080))
    (with slave (start-server 8081))
    (with replicator (replication/create (:instance @slave) (:url @master)))
    (with wait-for-replication 
      (fn [] (replication/wait-for @replicator 
               (docs/last-etag-in (get-in @master [:instance :storage])))))
    (before 
      (replication/start @replicator))
    (after
      (stop-server @master)
      (stop-server @slave)
      (replication/stop @replicator))

  (describe 
    "a bulk insert on master"

    (before
      (insert-2000-documents (:instance @master))
      (@wait-for-replication))

    (it "will cause the slave to contain all of the documents from the master"
      (should= 2000 (count (c/stream-seq (:url @slave)))))

    (it "will cause the slave to contain identical metadata"
      (should==
        (map :metadata (c/stream-seq (:url @master))) 
        (map :metadata (c/stream-seq (:url @slave)))))

    (it "will cause the slave contain identical documents"
      (should==
        (map :metadata (c/stream-seq (:url @master))) 
        (map :metadata (c/stream-seq (:url @slave))))))
  

  (describe 
    "Updating an existing replicated document in master"

    (before
      (c/put-document (:url @master) "ace-doc" { :hello "world"})
      (@wait-for-replication)
      (c/put-document (:url @master) "ace-doc" { :hello "dave"})
      (@wait-for-replication))

    (it "will cause the slave to contain the updated document"
      (should== { :hello "dave"} (c/get-document (:url @slave) "ace-doc"))))
  
  (describe
    "Deleting an existing replicated document in master"

    (before
      (c/put-document (:url @master) "ace-doc" { :hello "world"})
      (@wait-for-replication)
      (c/delete-document (:url @master) "ace-doc")
      (@wait-for-replication))

    (it "will no longer be available for retrieval in slave"
      (should-be-nil (c/get-document (:url @slave) "ace-doc"))))) 

