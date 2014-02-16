(ns lambda-shelf.database
  (:require [clojure.java.jdbc :as sql]))


(def spec
  (or (System/getenv "HEROKU_POSTGRESQL_BRONZE_URL")
      {:subprotocol "postgresql"
       :subname "the_shelf"
       :user "eve"
       :password "pg12"}))


(defn create-bookmark-table []
  (sql/db-do-commands
   spec
    (sql/create-table-ddl
     :bookmark
     [:id :serial "PRIMARY KEY"]
     [:title :text]
     [:url :text]
     ;;[:votes :int "NOT NULL" "DEFAULT 0"]
     [:date :timestamp "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"])))


(defn upgrade-bookmark-table []
  (sql/execute!
     spec
     ["ALTER TABLE bookmark ADD COLUMN votes integer NOT NULL DEFAULT 0"]))


(defn upgraded? []
  (let [columns (sql/query
               spec
               [(str "select column_name from information_schema.columns "
                     "where table_name='bookmark'")])]
  (-> (into #{} (map :column_name columns))
      (contains? "votes"))))


(defn upgrade []
  (when (not (upgraded?))
    (println "Upgrading database structure...") (flush)
    (upgrade-bookmark-table)
    (println "done")))


(defn migrated? []
  (-> (sql/query spec
                 [(str "select count(*) from information_schema.tables "
                       "where table_name='bookmark'")])
      first :count pos?))


(defn migrate []
  (when (not (migrated?))
    (print "Creating database structure...") (flush)
    (create-bookmark-table)
    (println " done")))


(defn insert-bookmark [{:keys [title url]}]
  (sql/insert!
   spec
   :bookmark
   [:title :url]
   [title url]))


(defn vote-bookmark [{:keys [id upvote]}]
  (let [current-votes (-> (sql/query spec [(str "select votes from bookmark where id=" id)])
                          first
                          :votes)]
    (sql/update!
     spec
     :bookmark
     {:votes (if upvote (inc current-votes) (dec current-votes))}
     ["id=?" id])))


(defn get-all-bookmarks []
  (vec (sql/query spec ["SELECT * FROM bookmark"])))


;; --- TESTING N STUFF ---
#_(sql/db-do-commands spec (sql/drop-table-ddl :bookmark))
#_(def test-data [{:title "the master himself" :url "https://github.com/kordano" }
                {:title "nomads blog" :url "http://functional-nomads.github.io"}
                {:title "solar raspberry" :url "http://www.instructables.com/id/Solar-Powered-Raspberry-Pi/?ALLSTEPS"}])

#_(migrate)
#_(doall (map insert-bookmark test-data))
#_(upgrade)
