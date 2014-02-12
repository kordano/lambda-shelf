(ns lambda-shelf.database
  (:require [clojure.java.jdbc :as sql]
            [clj-time.local :refer [local-now]]
            [clj-time.coerce :refer [to-timestamp]]))


(def heroku-db
  {:subprotocol "postgresql"
   :subname "df2mf9sv02i5a4"
   :user "buztdsjsyaokvj"
   :password "78PodlHiv1dnLGFtTigFGsTPn7"})

(def local-db
  {:subprotocol "postgresql"
   :subname "the_shelf"
   :user "eve"
   :password "pg12"})

(defn create-bookmark-table []
  (sql/db-do-commands
    local-db
    (sql/create-table-ddl
     :bookmark
     [:title :text]
     [:url :text]
     [:date :timestamp])))


(defn insert-bookmark [{:keys [title url]}]
  (sql/insert!
   local-db
   :bookmark
   [:title :url :date]
   [title url (to-timestamp (local-now))]))


(defn get-all-bookmarks []
  (vec (sql/query local-db ["SELECT * FROM bookmark"])))


;; --- TESTING N STUFF ---
#_(create-bookmark-table)
#_(sql/db-do-commands local-db (sql/drop-table-ddl :bookmark))
#_(def test-data [{:title "the master himself" :url "https://github.com/kordano" }
                {:title "nomads blog" :url "http://functional-nomads.github.io"}
                {:title "solar raspberry" :url "http://www.instructables.com/id/Solar-Powered-Raspberry-Pi/?ALLSTEPS"}])

#_(doall (map insert-bookmark test-data))
#_(get-all-bookmarks)
