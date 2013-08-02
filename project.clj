(defproject cravendb "0.0.0"
  :min-lein-version "2.2.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [clj-http "0.7.6"]
                 [ring "1.2.0"]
                 [compojure "1.1.5"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.fusesource.leveldbjni/leveldbjni-all "1.7"]
                 [me.raynes/fs "1.4.4"]
                 [speclj "2.5.0"]]
  :plugins [[speclj "2.5.0"]]
  :test-path "spec/"
  :main cravendb.server)

