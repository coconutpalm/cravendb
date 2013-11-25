(ns cravendb.scratch
  "The sole purpose of this file is to act as a place to play with stuff in repl"
  (:require [cravendb.database :as db]
            [cravendb.transaction :as t]
            [cravendb.testing :refer :all]
            [cravendb.client :as client]))

#_ (with-remote-instance 
     (db/put-document instance "1" { :username "bob"} {})
     (let [metadata (db/load-document-metadata instance "1" )] 
      (db/put-document instance "1" { :username "alice"} {})
      (db/put-document instance "1" { :username "craig"} metadata))
      #spy/p (db/conflicts instance)
     (db/clear-conflicts instance "1")
      #spy/p (db/conflicts instance)
     )


