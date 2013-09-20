(ns cravendb.http
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [cravendb.storage :as storage]
            [cravendb.indexing :as indexing] 
            [cravendb.query :as query] 
            [cravendb.indexstore :as indexes] 
            [cravendb.indexengine :as indexengine] 
            [cravendb.documents :as docs])

  (:use compojure.core
        [clojure.tools.logging :only (info error)]))

(defn create-http-server [db loaded-indexes]
  (defroutes app-routes

    (GET "/query/:index/:query" { params :params }
      (let [q (params :q)]
        (info "Querying for " q)
        (with-open [tx (.ensure-transaction db)]
          (query/execute tx loaded-indexes params))))

    (PUT "/doc/:id" { params :params body :body }
      (let [id (params :id) body (slurp body)]
        (info "putting a document in with id " id " and body " body)
        (with-open [tx (.ensure-transaction db)]
          (.commit! (docs/store-document tx id body)))))

    (GET "/doc/:id" [id] 
      (info "getting a document with id " id)
         (with-open [tx (.ensure-transaction db)]
          (or (docs/load-document tx id) { :status 404 })))

    (DELETE "/doc/:id" [id]
      (info "deleting a document with id " id)
        (with-open [tx (.ensure-transaction db)]
          (.commit! (docs/delete-document tx id))))

    (PUT "/index/:id" { params :params body :body }
      (let [id (params :id) body ((comp read-string slurp) body)]
        (info "putting an in with id " id " and body " body)
        (with-open [tx (.ensure-transaction db)]
          (.commit! 
            (indexes/put-index 
              tx {
                  :id id
                  :map (body :map)
                 })))))

    (GET "/index/:id" [id] 
      (info "getting an index with id " id)
         (with-open [tx (.ensure-transaction db)]
           (let [index (indexes/load-index tx id)]
             (if index
               (pr-str index)
               { :status 404 }))))

    (route/not-found "ZOMG NO, THIS IS NOT A VALID URL"))

  (handler/api app-routes))

(defn -main []
  (with-open [db (indexengine/setup 
                   (storage/create-storage "testdb"))]
    (try
      (run-jetty 
        (create-http-server db) {
                                 :port (Integer/parseInt (or (System/getenv "PORT") "8080")) :join? true})
      (finally
        (indexengine/teardown db)))  

    (info "Shutting down")))

