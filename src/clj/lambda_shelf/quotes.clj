(ns lambda-shelf.quotes
  (:require [clojure.string :refer [split]]))


(defn random-quote []
  (-> (slurp "resources/private/quotes.txt")
      (split #"\n")
      (rand-nth)))
