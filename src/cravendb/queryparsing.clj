(ns cravendb.queryparsing
  (:require [instaparse.core :as insta])
  (:require [clojure.tools.logging :refer [info error debug]])
  (:import 
           (org.apache.lucene.index Term)
           (org.apache.lucene.search TermQuery NumericRangeQuery PrefixQuery
                                     BooleanQuery BooleanClause  BooleanClause$Occur
                                     MatchAllDocsQuery)
           (org.apache.lucene.document Document Field Field$Store Field$Index 
                                      TextField IntField FloatField StringField)))


(def query-parser 
  (insta/parser
    "S = (Function | Wildcard) 
    Wildcard = '*'
    Whitespace = #'\\s+'
    <Function> = <'('>  (LessThanCall | GreaterThanCall | GreaterThanOrEqualCall | LessThanOrEqualCall | 
                            AndCall | OrCall | EqualsCall | NotCall | NotEqualsCall | ContainsCall | StartsWithCall )  <')'>   
    <Argument> = (Function | LiteralValue | Wildcard)

    AndCall = <'and'> (<Whitespace>* Argument)*
    OrCall = <'or'> (<Whitespace>* Argument )*
    NotCall = <'not'> (<Whitespace>* Argument )*

    EqualsCall = <'='> <Whitespace> FieldName <Whitespace> LiteralValue
    LessThanCall = <'<'> <Whitespace> FieldName <Whitespace> LiteralValue
    GreaterThanCall = <'>'> <Whitespace> FieldName <Whitespace> LiteralValue
    LessThanOrEqualCall = <'<='> <Whitespace> FieldName <Whitespace> LiteralValue
    GreaterThanOrEqualCall = <'>='> <Whitespace> FieldName <Whitespace> LiteralValue
    StartsWithCall = <'starts-with'> <Whitespace> FieldName <Whitespace> LiteralValue
    NotEqualsCall = <'not='> <Whitespace> FieldName <Whitespace> LiteralValue
    ContainsCall = <'contains'> <Whitespace> FieldName <Whitespace> StringValue
    <LiteralValue> = (NumericValue | StringValue)
    <FieldName> =  (StringValue | Symbol)
    Symbol =  #':([a-zA-Z]+)'
    StringValue = <'\"'> #'[a-zA-Z]+' <'\"'>
    NumericValue = #'[0-9]+' "
  ))

(defn create-equals-clause [[field-type field-name] [value-type value-value] ]
  (case value-type
    :StringValue (TermQuery. (Term. field-name value-value))
    :NumericValue (NumericRangeQuery/newIntRange field-name (Integer/parseInt value-value) (Integer/parseInt value-value) true true) ))

(defn create-starts-with-clause [[field-type field-name] [value-type value-value]]
  (case value-type
    :StringValue (PrefixQuery. (Term. field-name value-value))))

(defn create-less-than-clause [[field-type field-name] [value-type value-value]]
  (case value-type
    :NumericValue (NumericRangeQuery/newIntRange field-name Integer/MIN_VALUE (Integer/parseInt value-value) false false)))

(defn create-greater-than-clause [[field-type field-name] [value-type value-value]]
  (case value-type
    :NumericValue (NumericRangeQuery/newIntRange field-name (Integer/parseInt value-value) Integer/MAX_VALUE false false)))

(defn create-less-than-or-equal-clause [[field-type field-name] [value-type value-value]]
  (case value-type
    :NumericValue (NumericRangeQuery/newIntRange field-name Integer/MIN_VALUE (Integer/parseInt value-value) true true)))

(defn create-greater-than-or-equal-clause [[field-type field-name] [value-type value-value]]
  (case value-type
    :NumericValue (NumericRangeQuery/newIntRange field-name (Integer/parseInt value-value) Integer/MAX_VALUE true true)))

(defn create-boolean-query [occur expressions]
  (let [query (BooleanQuery.)]
    (doseq [sub-query expressions] (.add query sub-query occur ))
    query))

(defn create-and-call [& expressions]
  (create-boolean-query BooleanClause$Occur/MUST expressions))

(defn create-or-call [& expressions]
  (create-boolean-query BooleanClause$Occur/SHOULD expressions))

(defn create-not-call [& expressions]
  (let [query (create-boolean-query BooleanClause$Occur/MUST_NOT expressions)]
    (.add query (MatchAllDocsQuery.) BooleanClause$Occur/MUST)
    query))

(defn create-wildcard [in]
  (MatchAllDocsQuery.))

(defn wrap-with-wildcard [sub-query]
  (println sub-query)
  (let [query (BooleanQuery.)]
    (.add query (MatchAllDocsQuery.) BooleanClause$Occur/MUST) 
    (.add query sub-query BooleanClause$Occur/MUST) 
    query))

(defn merge-match-all [q]
  (assoc q 1 
    [:WrapWildcard (get q 1)]))

(defn is-positive-clause [q]
  (cond 
    (= (first q) :S) (some is-positive-clause (drop 1 q))
    (= (first q) :NotCall) false
    (= (first q) :AndCall) (some is-positive-clause (drop 1 q))
    (= (first q) :OrCall) (some is-positive-clause (drop 1 q))
    :else true))

(defn ensure-positivity [q]
  (if (is-positive-clause q) 
    (do
      (debug "Parsed query as " q) q) 
    (do
      (debug "Parsed query as " (merge-match-all q))
      (merge-match-all q))))

(defn to-lucene [query]
  (debug "Interpreting" query)
  (first (drop 1 (insta/transform 
    {
     :S nil
     :EqualsCall create-equals-clause 
     :LessThanCall create-less-than-clause
     :GreaterThanCall create-greater-than-clause
     :GreaterThanOrEqualCall create-greater-than-or-equal-clause
     :LessThanOrEqualCall create-less-than-or-equal-clause
     :StartsWithCall create-starts-with-clause
     :Wildcard create-wildcard
     :AndCall create-and-call
     :OrCall create-or-call
     :NotCall create-not-call
     }
    (query-parser query)))))

