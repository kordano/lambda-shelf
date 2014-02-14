(ns lambda-shelf.client
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [lambda-shelf.communicator :refer [post-edn get-edn]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))

;; fire up repl
#_(do
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))


(def app-state (atom {:bookmarks nil}))


(defn handle-url-change [e owner {:keys [url-text]}]
  (om/set-state! owner :url-text (.. e -target -value)))


(defn handle-title-change [e owner {:keys [title-text]}]
  (om/set-state! owner :title-text (.. e -target -value)))


(defn add-bookmark [app owner]
  (let [new-title (.-value (om/get-node owner "new-title"))
        new-url (.-value (om/get-node owner "new-url"))]
    (go
      (let [db-bookmarks (<!
                           (post-edn
                            "bookmark/add"
                            (str {:title new-title :url new-url})))]
        (om/transact!
         app
         :bookmarks
         (fn [bookmarks]
           (let [ids (into #{} (map :id bookmarks))
                 new-list (into []
                   (sort-by :date >
                            (into bookmarks
                                  (remove
                                   #(contains? ids (% :id))
                                   db-bookmarks))))]
             (.log js/console (str new-list))
             new-list
             )))
        (om/set-state! owner :url-text "")
        (om/set-state! owner :title-text "")))))


(defn bookmark-view [{:keys [title url date] :as bookmark} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [delete]}]
      (dom/tr nil
              (dom/td #js {:className "bookmark-date"} (dom/span nil (.toLocaleString date)))
              (dom/td #js {:className "bookmark-title"} (dom/a #js {:href url} title))
              ;;(dom/button #js {:onClick (fn [e] (put! delete @bookmark)) :className "remove-button"} "X")
              ))))


(defn bookmarks-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:delete (chan)
       :url-text ""
       :title-text ""})
    om/IWillMount
    (will-mount [_]
      (let [delete (om/get-state owner :delete)]
        (go (loop []
              (let [bookmark (<! delete)]
                (om/transact! app :bookmarks
                              (fn [xs] (into [] (remove #(= bookmark %) xs))))
                (recur))))))
    om/IRenderState
    (render-state [this state]
      (dom/div #js {:id "bookmarks-container"}
               (dom/div #js {:className "container-input"}
                        (dom/span nil
                                  (dom/input #js {:type "text"
                                                  :ref "new-url"
                                                  :value (:url-text state)
                                                  :placeholder "URL"
                                                  :onChange #(handle-url-change % owner state)}))
                        (dom/span nil
                                  (dom/input #js {:type "text"
                                                  :ref "new-title"
                                                  :value (:title-text state)
                                                  :placeholder "Title"
                                                  :onChange #(handle-title-change % owner state)}))
                        (dom/button #js {:onClick #(add-bookmark app owner)
                                         :className "add-button"} "ADD"))
               (dom/h2 #js {:className "container-header"} "Bookmarks")
               (dom/div #js {:className "container-list"}
                        (apply dom/table nil
                               (om/build-all bookmark-view (:bookmarks app))))))))


;; set initial state
(go
  (let [init-data (<! (get-edn "bookmark/init"))]
    (swap! app-state assoc :bookmarks (into [] (sort-by :date > init-data)))))


(om/root
  app-state
  bookmarks-view
  (. js/document (getElementById "bookmarks")))
