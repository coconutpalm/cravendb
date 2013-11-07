(ns cravendb.http
  (:require [org.httpkit.server :refer [run-server]]
            [clojure.edn :as edn]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [liberator.core :refer [resource]]
            [liberator.dev :refer [wrap-trace]]
            [cravendb.database :as db]
            [cravendb.stream :as stream]
            [cravendb.core :refer [zero-synctag]]
            )

  (:use compojure.core
        [clojure.tools.logging :only (info error debug)]))

(defn read-body [ctx] (edn/read-string (slurp (get-in ctx [:request :body]))))

(def accepted-types ["application/edn" "text/plain" "text/html"])

(defn standard-response [ctx data]
  (case (get-in ctx [:representation :media-type])
    "text/plain" (pr-str data)
    "application/edn" (pr-str data)
    "text/html" (str "<p>" (pr-str data) "</p>"))) 

(defn resource-exists [ctx rfn mfn]
  (if-let [resource (rfn)]
    {
     ::resource resource
     ::metadata (mfn)}
    false))

(defn synctag-from-metadata [ctx]
  (get-in ctx [::metadata :synctag]))

(defn craven-resource [])

(defn create-db-routes [instance]
  (routes
    (ANY "/document/:id" [id] 
      (resource
        :allowed-methods [:put :get :delete]
        :exists? (fn [ctx] (resource-exists ctx #(db/load-document instance id) #(db/load-document-metadata instance id)))
        :available-media-types accepted-types
        :etag (fn [ctx] (synctag-from-metadata ctx))
        :put! (fn [ctx] (db/put-document instance id (read-body ctx))) 
        :delete! (fn [_] (db/delete-document instance id)) 
        :handle-ok (fn [_] (standard-response _ (::resource _)))))

    (ANY "/index/:id" [id]
      (resource
        :allowed-methods [:put :get :delete]
        :exists? (fn [ctx] (resource-exists ctx #(db/load-index instance id) #(db/load-index-metadata instance id)))
        :available-media-types accepted-types
        :etag (fn [ctx] (synctag-from-metadata ctx))
        :put! (fn [ctx] (db/put-index instance (merge { :id id } (read-body ctx))))
        :delete! (fn [_] (db/delete-index instance id)) 
        :handle-ok (fn [_] (standard-response _ (::resource _)))))

    (ANY "/query/:index/:query" [index query]
       (resource
        :available-media-types accepted-types
        :handle-ok (fn [ctx] 
                     (standard-response 
                      ctx 
                      (db/query instance (get-in ctx [:request :params]))))))

    ;; ANOTHER UWAGA!!
    (ANY "/conflicts" []
       (resource
        :available-media-types accepted-types
        :handle-ok (fn [ctx] 
                     (standard-response ctx (db/conflicts instance)))))

    (ANY "/bulk" []
      (resource
        :allowed-methods [:post]
        :available-media-types ["application/edn"]
        :post! (fn [ctx] (pr-str (db/bulk instance (read-body ctx))))
        :handle-ok "OK"))
    
    ;; This will be probably a long polling thing
    ;; where I keep pumping data out as I get it
    ;; For now it will just return EVERYTHING in
    ;; a giant blob (UWAGA!!)
    (ANY "/stream" []
      (resource
        :allowed-methods [:get]
        :exists? true
        :available-media-types accepted-types
        :handle-ok 
        (fn [ctx]
          (standard-response 
            ctx
            (stream/from-synctag 
              instance
              (or (get-in ctx [:request :params :synctag]) (zero-synctag))))))))) 

(defn create-http-server [instance]
  (info "Setting up the bomb")
  (let [db-routes (create-db-routes instance)]
    (handler/api db-routes)))

(defn -main []
  (with-open [instance (db/create :path "testdb")]
    (run-server 
      (create-http-server instance) 
      { :port (Integer/parseInt (or (System/getenv "PORT") "8080")) :join? true}) 
    (debug "Shutting down")))

