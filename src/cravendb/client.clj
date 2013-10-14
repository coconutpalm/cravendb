(ns cravendb.client
  (:require [http.async.client :as http])
  (:require [cemerick.url :refer (url-encode)] ))

(defn url-for-doc-id [url id]
  (str url "/document/" id))

(defn url-for-index-id [url id]
  (str url "/index/" id))

(defn url-for-bulk-ops [url]
  (str url "/bulk"))

(defn url-for-query [url opts]
  (str 
    url "/query/"
    (opts :index) "/"
    (url-encode (opts :query))
    (if (opts :wait) "?wait=true")))

(defn to-db [data]
  (pr-str data))

(defn from-db [data]
  (if (nil? data)
    nil
    (read-string data)))

(defn force-into-list [result]
  (if (nil? result) ()
   (if (seq? result) result (seq [result]))))

(defn process-response [response]
  (-> response
      http/await
      http/string
      from-db))

(def default-headers { :accept "text/plain" })

(defn get-document [url id]
  (with-open [client (http/create-client)]
    (process-response 
      (http/GET client (url-for-doc-id url id) :headers default-headers))))

(defn put-document [url id doc]
  (with-open [client (http/create-client)]
    (process-response 
      (http/PUT client (url-for-doc-id url id) :body (to-db doc) :headers default-headers))))

(defn delete-document [url id]
  (with-open [client (http/create-client)]
    (process-response 
      (http/DELETE client (url-for-doc-id url id)))))

(defn put-index [url id mapf]
  (with-open [client (http/create-client)]
    (process-response 
      (http/PUT client (url-for-index-id url id) :body (to-db {:map mapf})))))

(defn bulk-operation [url ops]
  (with-open [client (http/create-client)]
    (process-response 
      (http/POST client (url-for-bulk-ops url) :body (to-db ops)))))

(defn get-index [url id]
  (with-open [client (http/create-client)]
    (process-response 
      (http/GET client (url-for-index-id url id)))))

(defn query [url opts]
  (with-open [client (http/create-client)]
    (force-into-list
      (process-response
        (http/GET client (url-for-query url opts))))))

