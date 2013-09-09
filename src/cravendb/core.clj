(ns cravendb.core
  (use [cravendb.storage]))

(defn expand-iterator-str [i]
  { :k (from-db-str (.getKey i))
    :v (from-db-str (.getValue i)) })

(defn extract-value-from-expanded-iterator [m] (m :v))

(defn integer-to-etag [integer]
  (format "%030d" integer))

(defn etag-to-integer [etag]
  (Integer/parseInt etag))

(defn zero-etag [] (integer-to-etag 0))

(defn newest-etag [one two]
  (integer-to-etag (max (etag-to-integer one) (etag-to-integer two))))

(defn next-etag [etag]
  (integer-to-etag (inc (etag-to-integer etag))))

