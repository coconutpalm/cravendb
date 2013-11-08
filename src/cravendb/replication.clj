(ns cravendb.replication
  (:require [cravendb.documents :as docs]
            [cravendb.core :refer [zero-synctag synctag-to-integer integer-to-synctag]]
            [cravendb.storage :as s]
            [cravendb.vclock :as v]
            [cravendb.inflight :as inflight]
            [cravendb.client :as c]))


(defn action-for
  [item]
  (if (:doc item) 
    :store
    :delete))

(defn store-last-synctag [tx url synctag]
  (s/store tx (str "replication-last-synctag-" url) (synctag-to-integer synctag)))

(defn store-last-total [tx url total]
  (s/store tx (str "replication-total-documents-" url) total))

(defn last-replicated-synctag [storage source-url]
  (integer-to-synctag
    (s/get-obj storage (str "replication-last-synctag-" source-url))))

(defn replication-total [storage source-url]
  (s/get-obj storage (str "replication-total-documents-" source-url)))

(defn replication-status 
  [storage source-url]
    {
     :last-synctag (last-replicated-synctag storage source-url)
     :total (replication-total storage source-url) })

(defn replicate-into [ifh items] 
  (let [txid (inflight/open ifh :replication)
        result (reduce 
          (fn [{:keys [total last-synctag] :as state} 
              {:keys [id doc metadata] :as item}]
            (case (action-for item)
              :delete (inflight/delete-document ifh txid id metadata)
              :store (inflight/add-document ifh txid id doc metadata))
            (assoc state
              :last-synctag (:synctag metadata)
              :total (inc total)))
            { :total 0 :last-synctag (zero-synctag) } items)] 

    (inflight/complete! ifh txid)
    result))

(defn replicate-from [ifh storage source-url items]
  (let [{:keys [last-synctag total]} (replicate-into ifh (take 100 items))] 
    (with-open [tx (s/ensure-transaction storage)] 
      (-> tx
        (store-last-synctag source-url last-synctag)
        (store-last-total source-url total)
        (s/commit!))))(drop 100 items))

(defn empty-replication-queue [ifh storage-destination source-url synctag]
  (loop [items (c/stream-seq source-url synctag)]
    (if (not (empty? items))
      (recur (replicate-from ifh storage-destination source-url items)))))

(defn pump-replication [ifh storage source-url]
  (empty-replication-queue 
      ifh
      storage     
      source-url
      (last-replicated-synctag storage source-url)))

(defn replication-loop [ifh storage source-url]
  (loop []
    (pump-replication ifh storage source-url)
    (Thread/sleep 50) 
    (recur)))

(defn start [handle]
  (assoc handle
    :future (future (replication-loop 
                      (get-in handle [:instance :ifh])
                      (get-in handle [:instance :storage]) 
                      (:source-url handle)))))

(defn stop [handle]
  (if-let [f (:future handle)]
    (future-cancel f)))

(defrecord ReplicationHandle [instance source-url]
  java.io.Closeable
  (close [this]
    (stop this)))

(defn create [instance source-url]
  (ReplicationHandle. instance source-url))

(defn wait-for  
  ([handle synctag] (wait-for handle synctag 1000))
  ([handle synctag timeout]
  (if (and 
        (not= synctag 
         (last-replicated-synctag (get-in handle [:instance :storage])
                        (:source-url handle)))
        (> timeout 0))
    (do
      (Thread/sleep 100)
      (wait-for handle synctag (- timeout 100))))))
