(ns cravendb.scratch
  "The sole purpose of this file is to act as a place to play with stuff in repl"
  (:use [cravendb.testing]
        [cravendb.core])

  (:require [ring.adapter.jetty :refer [run-jetty]]
            [cravendb.http :as http]
            [cravendb.client :as c] 
            [cravendb.database :as db]
            [me.raynes.fs :as fs]
            [cravendb.storage :as s]
            [cravendb.documents :as docs]
            [cravendb.core :refer [zero-etag]]
            [clojure.tools.logging :refer [info error debug]]
            ))

(defn start []
    (def instance 
      (do
        (fs/delete-dir "testdb")
        (db/create "testdb"))) 

    (def destinstance
      (do
        (fs/delete-dir "testdb2")
        (db/create "testdb2")))

    (def server 
     (run-jetty 
      (http/create-http-server instance) 
      { :port (Integer/parseInt (or (System/getenv "PORT") "8080")) :join? false}))
    
    (def destserver 
     (run-jetty 
      (http/create-http-server destinstance) 
      { :port (Integer/parseInt (or (System/getenv "PORT") "8081")) :join? false}))

    (db/bulk instance
      (map (fn [i]
      {
        :operation :docs-put
        :id (str "docs-" i)
        :document { :whatever (str "Trolololol" i)} 
        }) (range 0 5000))))

(defn stop []
   (.stop server)   
   (.stop destserver)   
   (.close instance)
   (.close destinstance))


(defn restart []
  (stop)
  (start))


#_ (start)
#_ (restart)
 
;; What I really want is a stream of the whole documents and their metadata
;; In the order in which they were written from a specific e-tag
;; What I'd probably do is keep documents in memory once written
;; because they'd need to be hit by both indexing and replication
;;
;; What I also probably want to do is keep a list of etags/ids written
;; and generate the stream information from that rather than hitting the database
;; ideally, consuming the stream shouldn't involve disk IO
;; We can probably even push the stream as an edn stream using edn/read-string


;; Can possibly do this with core.async
#_ (with-open [iter (s/get-iterator (:storage instance))] 
     (doall (map expand-document 
          (docs/iterate-etags-after iter (zero-etag)))))

#_ (c/get-document "http://localhost:8080" "docs-1")

(defn stream-sequence 
  ([url] (stream-sequence url (zero-etag)))
  ([url etag] (stream-sequence url etag (c/stream url etag)))
  ([url last-etag src]
   (if (empty? src) ()
     (let [{:keys [metadata doc] :as item} (first src)]
       (cons item (lazy-seq (stream-sequence url (:etag metadata) (rest src))))))))

(defn pump-readers [etag]
  (loop [last-etag etag
         items (stream-sequence "http://localhost:8080" etag)]
    (if (empty? items)
      (do
        (Thread/sleep 100)
        (pump-readers last-etag))
      (do
        (let [batch (take 100 items)] 
          (db/bulk 
            destinstance
            (map (fn [i] {:document (:doc i) :id (:id i) :operation :docs-put}) batch)
                   )

          (recur (get-in (last batch) [:metadata :etag]) (drop 100 items)))))))

#_ (def worker (future (pump-readers (zero-etag))))
#_ (future-cancel worker)

#_ (do
     (doall (drop 5020 (stream-sequence "http://localhost:8080"))) 
     (println "sink")
     )
