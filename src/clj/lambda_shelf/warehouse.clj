(ns lambda-shelf.warehouse
  (:refer-clojure :exclude [assoc! conj! dissoc! ==])
  (:require [clojure.core :as core]
            [lambda-shelf.database :as old]
            [com.ashafa.clutch :as cdb :refer [with-db get-database get-document put-document update-document all-documents]]))

(defn now [] (new java.util.Date))

(defn init-db []
  (cdb/get-database "bookmark"))


(defn insert-bookmark [{:keys [comment] :as bookmark}]
  (let [entry (dissoc bookmark :comment)]
    (put-document
     "bookmark"
     (assoc entry :votes 0 :date (now) :comments (if (= comment "") [] [comment])))))


(defn vote-bookmark [{:keys [_id upvote]}]
  (with-db "bookmark"
      (update-document (get-document _id) update-in [:votes] inc)))


(defn comment-bookmark [{:keys [_id comment]}]
  (with-db "bookmark"
    (update-document (get-document _id) update-in [:comments] #(conj % comment))))


(defn get-all-bookmarks []
  (let [ids (map #(:id %) (all-documents "bookmark"))]
    (mapv #(dissoc (get-document "bookmark" %) :_rev) ids)))


;; --- testing vars ---

#_(init-db)
