(ns lambda-shelf.quotes
  (:require [clojure.string :refer [split]]))


(defn random-quote []
  (-> (slurp "resources/private/quotes2.txt")
      (split #"\n")
      (rand-nth)))
