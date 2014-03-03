(ns lambda-shelf.database
  (:require [clojure.java.jdbc :as sql]
            [clojure.string :refer [join blank?]]))


(def spec
  (or (System/getenv "HEROKU_POSTGRESQL_BRONZE_URL")
      {:subprotocol "postgresql"
       :subname "the_shelf"
       :user "eve"
       :password "pg12"}))

(def test-data [{:title "the master himself" :url "https://github.com/kordano" :comment "this"}
                {:title "nomads blog" :url "http://functional-nomads.github.io" :comment "that"}
                {:title "solar raspberry" :url "http://www.instructables.com/id/Solar-Powered-Raspberry-Pi/?ALLSTEPS"}])

;; --- database management bamboozle ---

(defn contains-elements? [collection elements]
  (let [coll-set (into #{} collection)]
      (every? #(contains? coll-set %) elements)))


(defn create-bookmark-table []
  (sql/db-do-commands
   spec
    (sql/create-table-ddl
     :bookmark
     [:id :serial "PRIMARY KEY"]
     [:title :text]
     [:url :text]
     [:votes :int "NOT NULL" "DEFAULT 0"]
     [:comments :text]
     [:date :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"])))


(defn upgrade-votes-bookmark-table []
  (sql/execute!
     spec
     ["ALTER TABLE bookmark ADD COLUMN votes integer NOT NULL DEFAULT 0"]))


(defn upgrade-comments-bookmark-table []
  (sql/execute!
     spec
     ["ALTER TABLE bookmark ADD COLUMN comments text DEFAULT '[]'"]))


(defn upgraded? [table-name column]
  (let [columns (->> (sql/query
                   spec
                   [(str "select column_name from information_schema.columns "
                         "where table_name='" table-name "'")])
                     (map :column_name))]
    (contains? (into #{} columns) column)))



(defn upgrade [table column upgrade-function]
  (when (not (upgraded? table column))
    (println (str "Upgrading " table " database structure...")) (flush)
    (upgrade-function)
    (println "done")))


(defn migrated? [table-name]
  (-> (sql/query
       spec
       [(str "select count(*) from information_schema.tables "
             "where table_name='" table-name "'")])
      first :count pos?))


(defn migrate [table-name create-function]
  (when (not (migrated? table-name))
    (println (str "Creating " table-name " database structure...")) (flush)
    (create-function)
    (println "done")))


;; --- database I/O ---
(defn insert-bookmark [{:keys [title url comment]}]
  (sql/insert!
   spec
   :bookmark
   [:title :url :comments]
   [title url (if (blank? comment) (str []) (str [comment]))]))


(defn vote-bookmark [{:keys [id upvote]}]
  (let [current-votes (-> (sql/query spec [(str "select votes from bookmark where id=" id)])
                          first
                          :votes)]
    (sql/update!
     spec
     :bookmark
     {:votes (if upvote (inc current-votes) (dec current-votes))}
     ["id=?" id])))


(defn comment-bookmark [{:keys [id comment]}]
  (let [current-comments (-> (sql/query spec [(str "select comments from bookmark where id=" id)])
                          first
                          :comments
                          read-string)]
    (sql/update!
     spec
     :bookmark
     {:comments (str (conj current-comments comment))}
     ["id=?" id])))



(defn get-all-bookmarks []
  (let [all-bookmarks (sql/query spec ["SELECT * FROM bookmark"])]
    (vec (map #(update-in % [:comments] (fn [c] (read-string c))) all-bookmarks))))


(defn initialize-databases []
   (migrate "bookmark" create-bookmark-table)
   (upgrade "bookmark" "comments" upgrade-comments-bookmark-table))

#_(doall (map insert-bookmark test-data))

;; --- TESTING N STUFF ---
#_(sql/db-do-commands spec (sql/drop-table-ddl :bookmark))

#_(initialize-databases)
