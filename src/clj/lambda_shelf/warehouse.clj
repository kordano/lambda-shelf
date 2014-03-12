(ns lambda-shelf.warehouse
  (:refer-clojure :exclude [assoc! conj! dissoc! ==])
  (:require [clojure.core :as core]
            [lambda-shelf.database :as old]
            [com.ashafa.clutch.utils :as utils]
            [com.ashafa.clutch :as cdb :refer [with-db get-database get-document put-document update-document all-documents]]))

(defn now [] (new java.util.Date))


(def host (or (System/getenv "DB_PORT_5984_TCP_ADDR") "localhost"))


(defn database-url [database]
  (utils/url (utils/url (str "http://" host ":5984")) database))

(defn init-db []
  (get-database (database-url "bookmark")))


(defn insert-bookmark [{:keys [comment] :as bookmark}]
  (with-db (database-url "bookmark")
    (let [entry (dissoc bookmark :comment)]
      (put-document
       (assoc entry :votes 0 :date (now) :comments (if (= comment "") [] [comment]))))))


(defn vote-bookmark [{:keys [_id upvote]}]
  (with-db (database-url "bookmark")
      (update-document (get-document _id) update-in [:votes] inc)))


(defn comment-bookmark [{:keys [_id comment]}]
  (with-db (database-url "bookmark")
    (update-document (get-document _id) update-in [:comments] #(conj % comment))))


(defn get-all-bookmarks []
  (with-db (database-url "bookmark")
    (let [ids (map #(:id %) (all-documents))]
      (mapv #(dissoc (get-document %) :_rev) ids))))


;; --- testing vars ---

#_(init-db)
(println (get-all-bookmarks))
