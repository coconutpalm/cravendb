(ns cravendb.testing
  (:require [me.raynes.fs :as fs]
            [ring.adapter.jetty :refer [run-jetty]]
            [cravendb.documents :as docs]
            [cravendb.indexengine :as indexengine]
            [cravendb.storage :as s]
            [cravendb.http :as http]
            [cravendb.database :as database]))
      

(defn clear-test-data []
  (fs/delete-dir "testdir"))

(defn with-db [testfn]
  (clear-test-data)
  (with-open [db (s/create-storage "testdir")]
    (testfn db))
  (clear-test-data))


(defn with-full-setup [testfn]
  (clear-test-data)
  (with-open [db (s/create-storage "testdir")
               engine (indexengine/create-engine db)]
      (let [result (try       
        (indexengine/start engine)
        (try
          (testfn db engine)
          (finally (indexengine/stop engine))))]
        (clear-test-data)      
        result)))

(defn inside-tx [testfn]
  (with-db 
    (fn [db]
      (with-open [tx (s/ensure-transaction db)]
        (testfn tx)))))

(defn with-test-server [testfn]
  (clear-test-data)
  (with-open [instance (database/create "testdir")]
    (try (let [server (run-jetty 
                   (http/create-http-server instance) 
                    { :port 9000 :join? false} )]
      (try
        (testfn)
        (finally
          (.stop server))))))
  (clear-test-data))
