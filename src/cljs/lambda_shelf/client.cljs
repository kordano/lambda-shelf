(ns lambda-shelf.client
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [lambda-shelf.communicator :refer [post-edn get-edn]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))


(defn log [arg]
  (.log js/console (str arg)))


(log "HAIL TO THE LAMBDA!")

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


(defn apply-to-entry [x k f]
  "Apply function f to key k in entry x"
  (fn [xs]
    (vec
     (map #(if (= x %) (assoc % k (f (k %))) %) xs))))


(defn add-bookmark [app owner]
  "read input data, send it to server and update dom"
  (let [new-title (.-value (om/get-node owner "new-title"))
        new-url (.-value (om/get-node owner "new-url"))
        package (str {:title new-title :url new-url})]
    (go
      (>! (om/get-state owner :incoming) (<! (post-edn "bookmark/add" package)))
      (om/set-state! owner :url-text "")
      (om/set-state! owner :title-text ""))))



(defn bookmark-view [{:keys [title url date id votes] :as bookmark} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [upvote downvote]}]
      (dom/tr nil
              (dom/td nil
                      (dom/div
                       #js {:onClick
                            (fn [e]
                              (do
                                (post-edn "bookmark/vote" (str {:id id :upvote true}))
                                (put! upvote @bookmark))) :className "arrow up"})

                      (dom/br nil)

                      (dom/div
                       #js {:onClick
                            (fn [e]
                              (do
                                (post-edn "bookmark/vote" (str {:id id :upvote false}))
                                (put! downvote @bookmark))) :className "arrow down"}))

              (dom/td #js {:className "bookmark-voting"} (dom/a nil votes))
              (dom/td #js {:className "bookmark-title"} (dom/a #js {:href url} title))
              (dom/td #js {:className "bookmark-date"} (dom/a nil (.toLocaleString date)))))))


(defn bookmarks-view [app owner]
  (reify

    om/IInitState
    (init-state [_]
      {:upvote (chan)
       :downvote (chan)
       :incoming (chan)
       :url-text ""
       :title-text ""
       :shown 8})

    om/IWillMount
    (will-mount [_]
      (let [upvote (om/get-state owner :upvote)
            downvote (om/get-state owner :downvote)
            incoming (om/get-state owner :incoming)]
        (go
          (loop []
            (let [[v c] (alts! [upvote downvote incoming])]
              (condp = c
                upvote (om/transact! app :bookmarks (apply-to-entry v :votes inc))
                downvote (om/transact! app :bookmarks (apply-to-entry v :votes dec))
                incoming (om/transact! app :bookmarks
                                       (fn [bookmarks]
                                         (let [ids (into #{} (map :id bookmarks))]
                                           (into []
                                                 (sort-by :date >
                                                          (into bookmarks
                                                                (remove #(contains? ids (% :id)) v))))))))
              (recur))))
        (go
          (while true
            (>! incoming (<! (get-edn "bookmark/init")))
            (<! (timeout 60000))))))

    om/IRenderState
    (render-state [this {:keys [upvote downvote incoming shown] :as state}]
      (dom/div #js {:id "bookmark-container" :className "container"}
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
                                                  :onChange #(handle-title-change % owner state)
                                                  :onKeyPress #(when (== (.-keyCode %) 13)
                                                                 (add-bookmark app owner))}))
                        (dom/button #js {:onClick #(add-bookmark app owner)
                                         :className "add-button"} "ADD"))
               (dom/div #js {:className "container-header"}
                        (dom/a nil "Bookmarks"))
               (dom/div #js {:className "container-list"}
                        (apply dom/table nil
                               (om/build-all bookmark-view (take shown (:bookmarks app))
                                             {:init-state {:upvote upvote :downvote downvote :incoming incoming}}))
                        (dom/button #js {:onClick #(om/set-state! owner :shown (* shown 2))
                                         :className "add-button"} "Show more"))))))


(om/root
  app-state
  bookmarks-view
  (. js/document (getElementById "bookmarks")))
